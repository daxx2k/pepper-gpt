package com.softbankrobotics.pepper.pepperGPT;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import com.aldebaran.qi.sdk.QiContext;
import com.aldebaran.qi.sdk.builder.TakePictureBuilder;
import com.aldebaran.qi.sdk.object.image.EncodedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.regex.Pattern;

final class PepperSight {
    private static final Pattern LOOK = Pattern.compile(
            "^(?:pepper[\\s,:.!-]+)?(?:guarda(?:[.!?]*|[, ]+(?:e\\s+)?(?:dimmi|descrivi|quest[oa]|qui|davanti|intorno)\\b.*)"
            + "|(?:dimmi\\s+)?(?:che\\s+)?cosa\\s+vedi\\b.*"
            + "|look(?:[.!?]*|[, ]+(?:and\\s+)?(?:tell me|at|around|ahead)\\b.*)"
            + "|(?:tell me\\s+what you see|what (?:can|do) you see)\\b.*)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern EXCLUDE = Pattern.compile(
            "\\b(?:trasforma|trasformami|transform|turn me into|disegna|genera|generate)\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final Pattern DO_NOT_CAPTURE = Pattern.compile(
            "\\b(?:non\\s+(?:scattare|fare\\s+(?:una\\s+)?foto|guardare|usare\\s+la\\s+fotocamera)"
            + "|senza\\s+(?:scattare|fare\\s+(?:una\\s+)?foto|fotocamera)"
            + "|(?:do not|don['’]t|without)\\s+(?:take|taking|look|use|using|capture))\\b",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    static boolean allowed(String spoken) {
        return !EXCLUDE.matcher(spoken).find() && !DO_NOT_CAPTURE.matcher(spoken).find();
    }

    static boolean requested(String spoken) {
        return LOOK.matcher(spoken.trim()).matches() && allowed(spoken);
    }

    static String capture(QiContext qi) throws Exception {
        Object image = TakePictureBuilder.with(qi).build().run().getImage();
        ByteBuffer data = data(image);
        if (data == null && image != null) {
            try { data = data(image.getClass().getMethod("getValue").invoke(image)); }
            catch (ReflectiveOperationException ignored) {}
        }
        if (data == null) throw new IllegalStateException("Camera returned no encoded image");
        ByteBuffer bytes = data.duplicate();
        bytes.rewind();
        if (bytes.remaining() > 8000000) throw new IllegalStateException("Unexpected camera image size");
        byte[] encoded = new byte[bytes.remaining()];
        bytes.get(encoded);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(encoded, 0, encoded.length, bounds);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 800)
            options.inSampleSize *= 2;
        Bitmap frame = BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
        if (frame == null) throw new IllegalStateException("Camera image could not be decoded");
        try {
            ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
            if (!frame.compress(Bitmap.CompressFormat.JPEG, 72, jpeg) || jpeg.size() > 150000)
                throw new IllegalStateException("Camera image could not be compressed");
            return "data:image/jpeg;base64," + Base64.encodeToString(jpeg.toByteArray(), Base64.NO_WRAP);
        } finally { frame.recycle(); }
    }

    private static ByteBuffer data(Object image) throws Exception {
        if (image instanceof EncodedImage) return ((EncodedImage)image).getData();
        if (image != null) {
            try {
                Object data = image.getClass().getMethod("getData").invoke(image);
                if (data instanceof ByteBuffer) return (ByteBuffer)data;
            } catch (NoSuchMethodException ignored) {}
        }
        return null;
    }
}
