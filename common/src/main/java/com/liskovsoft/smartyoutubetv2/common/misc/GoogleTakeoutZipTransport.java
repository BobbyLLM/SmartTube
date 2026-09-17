package com.liskovsoft.smartyoutubetv2.common.misc;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;

/** Validates the picker result before handing the original stream to the Takeout parser. */
public final class GoogleTakeoutZipTransport {
    private static final int ZIP_SIGNATURE_SIZE = 4;

    private GoogleTakeoutZipTransport() {
    }

    public static PushbackInputStream open(InputStream input) throws IOException {
        if (input == null) throw new IOException("Selected Takeout document is empty");

        PushbackInputStream stream = new PushbackInputStream(input, ZIP_SIGNATURE_SIZE);
        byte[] signature = new byte[ZIP_SIGNATURE_SIZE];
        int count = 0;
        while (count < signature.length) {
            int read = stream.read(signature, count, signature.length - count);
            if (read < 0) break;
            count += read;
        }
        if (!isZipSignature(signature, count)) {
            stream.close();
            throw new IOException("Selected document is not a complete Google Takeout ZIP");
        }
        stream.unread(signature, 0, count);
        return stream;
    }

    static boolean isZipSignature(byte[] signature, int length) {
        if (signature == null || length < ZIP_SIGNATURE_SIZE) return false;
        return signature[0] == 'P' && signature[1] == 'K'
                && ((signature[2] == 3 && signature[3] == 4)
                || (signature[2] == 5 && signature[3] == 6)
                || (signature[2] == 7 && signature[3] == 8));
    }
}
