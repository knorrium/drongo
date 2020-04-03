package com.sparrowwallet.drongo.protocol;

import com.sparrowwallet.drongo.Utils;
import com.sparrowwallet.drongo.crypto.ECKey;
import org.bouncycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

import static com.sparrowwallet.drongo.protocol.ScriptOpCodes.*;

public class ScriptChunk {
    /** Operation to be executed. Opcodes are defined in {@link ScriptOpCodes}. */
    public final int opcode;

    /**
     * For push operations, this is the vector to be pushed on the stack. For {@link ScriptOpCodes#OP_0}, the vector is
     * empty. Null for non-push operations.
     */
    public final byte[] data;

    private int startLocationInProgram;

    public ScriptChunk(int opcode, byte[] data) {
        this(opcode, data, -1);
    }

    public ScriptChunk(int opcode, byte[] data, int startLocationInProgram) {
        this.opcode = opcode;
        this.data = data;
        this.startLocationInProgram = startLocationInProgram;
    }

    public boolean equalsOpCode(int opcode) {
        return opcode == this.opcode;
    }

    /**
     * If this chunk is a single byte of non-pushdata content (could be OP_RESERVED or some invalid Opcode)
     */
    public boolean isOpCode() {
        return opcode > OP_PUSHDATA4;
    }

    public void write(OutputStream stream) throws IOException {
        if (isOpCode()) {
            if(data != null) throw new IllegalStateException("Data must be null for opcode chunk");
            stream.write(opcode);
        } else if (data != null) {
            if (opcode < OP_PUSHDATA1) {
                if(data.length != opcode) throw new IllegalStateException("Data length must equal opcode value");
                stream.write(opcode);
            } else if (opcode == OP_PUSHDATA1) {
                if(data.length > 0xFF) throw new IllegalStateException("Data length must be less than or equal to 256");
                stream.write(OP_PUSHDATA1);
                stream.write(data.length);
            } else if (opcode == OP_PUSHDATA2) {
                if(data.length > 0xFFFF) throw new IllegalStateException("Data length must be less than or equal to 65536");
                stream.write(OP_PUSHDATA2);
                Utils.uint16ToByteStreamLE(data.length, stream);
            } else if (opcode == OP_PUSHDATA4) {
                if(data.length > Script.MAX_SCRIPT_ELEMENT_SIZE) throw new IllegalStateException("Data length must be less than or equal to " + Script.MAX_SCRIPT_ELEMENT_SIZE);
                stream.write(OP_PUSHDATA4);
                Utils.uint32ToByteStreamLE(data.length, stream);
            } else {
                throw new RuntimeException("Unimplemented");
            }
            stream.write(data);
        } else {
            stream.write(opcode); // smallNum
        }
    }

    public int getOpcode() {
        return opcode;
    }

    public byte[] getData() {
        return data;
    }

    public boolean isSignature() {
        if(data == null || data.length == 0) {
            return false;
        }

        try {
            ECKey.ECDSASignature.decodeFromDER(data);
        } catch(SignatureDecodeException e) {
            return false;
        }

        return true;
    }

    public boolean isScript() {
        if(data == null || data.length == 0) {
            return false;
        }

        try {
            new Script(data);
        } catch(ProtocolException e) {
            return false;
        }

        return true;
    }

    public boolean isPubKey() {
        if(data == null || data.length == 0) {
            return false;
        }

        return ECKey.isPubKey(data);
    }

    public byte[] toByteArray() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            write(stream);
        } catch (IOException e) {
            // Should not happen as ByteArrayOutputStream does not throw IOException on write
            throw new RuntimeException(e);
        }
        return stream.toByteArray();
    }

    /*
     * The size, in bytes, that this chunk would occupy if serialized into a Script.
     */
    public int size() {
        final int opcodeLength = 1;

        int pushDataSizeLength = 0;
        if (opcode == OP_PUSHDATA1) pushDataSizeLength = 1;
        else if (opcode == OP_PUSHDATA2) pushDataSizeLength = 2;
        else if (opcode == OP_PUSHDATA4) pushDataSizeLength = 4;

        final int dataLength = data == null ? 0 : data.length;

        return opcodeLength + pushDataSizeLength + dataLength;
    }

    static int getOpcodeForLength(int length) {
        if(length <= 0xFF) {
            return OP_PUSHDATA1;
        }
        if(length <= 0xFFFF) {
            return OP_PUSHDATA2;
        }
        return OP_PUSHDATA4;
    }

    public String toString() {
        if (data == null) {
            return "OP_" + getOpCodeName(opcode);
        }
        if (data.length == 0) {
            return "OP_0";
        }

        return Hex.toHexString(data);
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptChunk other = (ScriptChunk) o;
        return opcode == other.opcode && Arrays.equals(data, other.data);
    }

    public int hashCode() {
        return Objects.hash(opcode, Arrays.hashCode(data));
    }
}
