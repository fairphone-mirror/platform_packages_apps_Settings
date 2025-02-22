package com.android.settings.anc.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.text.TextUtils;
import android.util.Log;

import com.android.settings.R;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class CommonUtil {
    public static final int YUV = 1;
    public static int OTHER_PIC = 2;
    public static int width = 640;
    public static int height = 480;
    public static String Memory_flag = new String("unavaliable memory");

    public static Bitmap getBitmapFromYUVData(byte[] data, int yuvType, int height, int width) {
        YuvImage yuvImage = new YuvImage(data, yuvType, width, height, null);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 100, byteArrayOutputStream);
        byte[] jpegData = byteArrayOutputStream.toByteArray();
        // 获取照相后的bitmap
        Bitmap tmpBitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.length);
        tmpBitmap = tmpBitmap.copy(Config.ARGB_8888, true);
        return tmpBitmap;
    }

    /**
     * 时间格式化(格式到秒)
     */
    public static String getFormatterDate(long time) {
        Date d = new Date(time);
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        String data = formatter.format(d);
        return data;
    }

    /**
     * 获取bitmap的灰度图像
     */
    public static byte[] getGrayscale(Bitmap bitmap) {
        if (bitmap == null)
            return null;

        byte[] ret = new byte[bitmap.getWidth() * bitmap.getHeight()];
        for (int j = 0; j < bitmap.getHeight(); ++j)
            for (int i = 0; i < bitmap.getWidth(); ++i) {
                int pixel = bitmap.getPixel(i, j);
                int red = ((pixel & 0x00FF0000) >> 16);
                int green = ((pixel & 0x0000FF00) >> 8);
                int blue = pixel & 0x000000FF;
                ret[j * bitmap.getWidth() + i] = (byte) ((299 * red + 587 * green + 114 * blue) / 1000);
            }
        return ret;
    }

    public static byte[] readFileInfo(String pathName) {
        InputStream inputStream = null;
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int count = -1;
        try {
            inputStream = new FileInputStream(pathName);
            while ((count = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, count);
            }
            byteArrayOutputStream.close();
        } catch (IOException e) {
            return null;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    public static Bitmap readBitmapFromFile(String filePath) {
        BitmapFactory.Options decodeBitmapOptions = new BitmapFactory.Options();
        decodeBitmapOptions.inDither = false;
        Bitmap bitmap = null;
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(new File(filePath));
            bitmap = BitmapFactory.decodeStream(inputStream, null, decodeBitmapOptions);
        } catch (IOException ignored) {
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ignored) {
            }
        }
        return bitmap;
    }

    /**
     * 获取APP版本名
     */
    public static String getVersionName(Context context) {
        try {
            String versionName = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
            return versionName;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 镜像旋转
     */
    public static Bitmap convert(Bitmap bitmap, boolean mIsFrontalCamera) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();
        Bitmap newbBitmap = Bitmap.createBitmap(w, h, Config.ARGB_8888);// 创建一个新的和SRC长度宽度一样的位图
        Canvas cv = new Canvas(newbBitmap);
        Matrix m = new Matrix();
        // m.postScale(1, -1); //镜像垂直翻转
        if (mIsFrontalCamera) {
            m.postScale(-1, 1); // 镜像水平翻转
        }
        // m.postRotate(-90); //旋转-90度
        Bitmap bitmap2 = Bitmap.createBitmap(bitmap, 0, 0, w, h, m, true);
        cv.drawBitmap(bitmap2, new Rect(0, 0, bitmap2.getWidth(), bitmap2.getHeight()), new Rect(0, 0, w, h), null);
        return newbBitmap;
    }

    public static Bitmap bgr2bitmap(byte[] img, int width, int height) {
        int[] tmp = new int[width * height];
        for (int i = 0; i < width * height; ++i) {
            int b = 0xff & ((int) img[3 * i]);
            int g = 0xff & ((int) img[3 * i + 1]);
            int r = 0xff & ((int) img[3 * i + 2]);
            tmp[i] = (255 << 24) | (r << 16) | (g << 8) | (b);
        }
        return Bitmap.createBitmap(tmp, width, height, Config.ARGB_8888);
    }

    public static byte[] geneSourceData(String sourcePath, Integer width, Integer height, int sourceType) {
        byte[] sourceByte;
        if (sourceType == YUV) {
            sourceByte = getNV21FromFile(width, height, sourcePath);
        } else {
            sourceByte = getSampleBitmapSource(sourcePath);
        }
        return sourceByte;
    }


    public static byte[] getNV21FromFile(int inputWidth, int inputHeight, String filePath) {
        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        try {
            FileInputStream inputStream = new FileInputStream(filePath);
            int offset = 0;
            int numRead = 0;
            while (offset < yuv.length
                    && (numRead = inputStream.read(yuv, offset, yuv.length - offset)) >= 0) {
                offset += numRead;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return yuv;
    }

    public static byte[] getSampleBitmapSource(String sourcePath) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sourcePath, options);
        int scale = Math.max(options.outHeight, options.outWidth) / 640;
        if (scale < 1) {
            scale = 1;
        }
        options.inSampleSize = scale;
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(sourcePath, options);
        if (bitmap == null) {
            return null;
        }
        Bitmap bitmap1 = adjustPhotoRotation(bitmap, 90);

        return getNV21(bitmap1.getWidth(), bitmap1.getHeight(), bitmap1);

    }

    public static byte[] getNV21(int inputWidth, int inputHeight, Bitmap scaled) {

        int[] argb = new int[inputWidth * inputHeight];

        scaled.getPixels(argb, 0, inputWidth, 0, 0, inputWidth, inputHeight);

        byte[] yuv = new byte[inputWidth * inputHeight * 3 / 2];
        encodeYUV420SP(yuv, argb, inputWidth, inputHeight);

        scaled.recycle();

        return yuv;
    }

    public static void encodeYUV420SP(byte[] yuv420sp, int[] argb, int width, int height) {
        final int frameSize = width * height;

        int yIndex = 0;
        int uvIndex = frameSize;

        int a, R, G, B, Y, U, V;
        int index = 0;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {

                a = (argb[index] & 0xff000000) >> 24; // a is not used obviously
                R = (argb[index] & 0xff0000) >> 16;
                G = (argb[index] & 0xff00) >> 8;
                B = (argb[index] & 0xff) >> 0;

                // well known RGB to YUV algorithm
                Y = ((66 * R + 129 * G + 25 * B + 128) >> 8) + 16;
                U = ((-38 * R - 74 * G + 112 * B + 128) >> 8) + 128;
                V = ((112 * R - 94 * G - 18 * B + 128) >> 8) + 128;

                // NV21 has a plane of Y and interleaved planes of VU each sampled by a factor of 2
                //    meaning for every 4 Y pixels there are 1 V and 1 U.  Note the sampling is
                // every other
                //    pixel AND every other scanline.
                yuv420sp[yIndex++] = (byte) ((Y < 0) ? 0 : ((Y > 255) ? 255 : Y));
                if (j % 2 == 0 && index % 2 == 0) {
                    yuv420sp[uvIndex++] = (byte) ((V < 0) ? 0 : ((V > 255) ? 255 : V));
                    yuv420sp[uvIndex++] = (byte) ((U < 0) ? 0 : ((U > 255) ? 255 : U));
                }

                index++;
            }
        }
    }

    public static String saveNV21(Context mContext, byte[] data, int w, int h, String path, String name) {
        if (data == null)
            return null;

        File mediaStorageDir = mContext.getExternalFilesDir("anc");
        File dir = new File(mediaStorageDir, path);

        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }
        String bitmapFileName = name + ".nv21." + w + "." + h;
        // String bitmapFileName = System.currentTimeMillis() + "";
        File file = new File(dir, bitmapFileName);
        FileOutputStream fos = null;
        String ret = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(data);
            ret = file.getAbsolutePath();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    /**
     * 保存bitmap至指定Picture文件夹
     */
    public static String saveBitmap(Context mContext, Bitmap bitmaptosave, String path, String name) {
        if (bitmaptosave == null)
            return null;

        File mediaStorageDir = mContext.getExternalFilesDir("anc");
        File dir = new File(mediaStorageDir, path);

        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }
        String bitmapFileName = name + ".png";
        // String bitmapFileName = System.currentTimeMillis() + "";
        File file = new File(dir, bitmapFileName);
        FileOutputStream fos = null;
        String ret = null;
        try {
            fos = new FileOutputStream(file);
            boolean successful = bitmaptosave.compress(Bitmap.CompressFormat.PNG, 75, fos);

            if (successful) ret = file.getAbsolutePath();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    public static List<String> readModelInfo(Context context, int res) {
        List<String> list = new ArrayList<>();
        InputStream inputStream = null;
        BufferedReader bufferedReader = null;

        try {
            inputStream = context.getResources().openRawResource(res);
            bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"));

            String str = null;
            while((str = bufferedReader.readLine()) != null){
                list.add(str);
            }

            return list;
        } catch (Exception ex) {
            return list;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String readRaw(Context context, int res) {
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;

        try {
            inputStream = context.getResources().openRawResource(res);
            outputStream = new ByteArrayOutputStream();

            byte[] bytes = new byte[1024];
            int readSize = 0;
            while ((readSize = inputStream.read(bytes)) != -1) {
                outputStream.write(bytes, 0, readSize);
            }

            String content = outputStream.toString("utf-8");
            return content;
        } catch (Exception ex) {
            return "";
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String saveRaw(Context context, int res, String path, String name) {
        File dir = new File(context.getExternalFilesDir(Constants.UNLOCK_FACE_FOLDER_PATH), path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                return null;
            }
        }
        File file = new File(dir, name);
        String ret = null;
        int rawSize = getRawFileSize(context, res);
        if(file.exists() && file.isFile() && (rawSize == file.length())){
            ret = file.getAbsolutePath();
        } else {
            File SDcard_path = Environment.getExternalStorageDirectory();
            StatFs stat = new StatFs(SDcard_path.getPath());
            long avaliableBlocks = stat.getAvailableBlocks();

            FileOutputStream fos = null;
            InputStream is = null;

            try {
                int count;
                byte[] buffer = new byte[1024];
                fos = new FileOutputStream(file);
                is = context.getResources().openRawResource(res);
                long source_size = is.available();
                if (avaliableBlocks < source_size / 4096 + 256)
                    return Memory_flag;
                while ((count = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, count);
                }

                ret = file.getAbsolutePath();
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            } finally {
                try {
                    if (fos != null) fos.close();
                    if (is != null) is.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return ret;
    }

    public static Bitmap adjustPhotoRotation(Bitmap bm, final int orientationDegree) {
        Matrix m = new Matrix();
        m.setRotate(orientationDegree, (float) bm.getWidth() / 2, (float) bm.getHeight() / 2);

        try {
            Bitmap bm1 = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), m, true);
            return bm1;
        } catch (OutOfMemoryError ex) {
        }
        return null;
    }

    public static void copyFile(String oldPath, String newPath) {
        InputStream inStream = null;
        FileOutputStream outStream = null;
        try {
            File oldfile = new File(oldPath);
            if (oldfile.exists()) { //文件存在时
                inStream = new FileInputStream(oldPath); //读入原文件
                outStream = new FileOutputStream(newPath);
                byte[] buffer = new byte[1014];
                int length;
                while ((length = inStream.read(buffer)) != -1) {
                    outStream.write(buffer, 0, length);
                }
                inStream.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (outStream != null) {
                try {
                    outStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (inStream != null) {
                try {
                    inStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * 根据yuv图像得到bitmap(用于保存底库图片）
     *
     * @param data         YUV数据
     * @param roi          图像矩形
     * @param angle        图像旋转角度
     * @param isBackCamera 是否是后置摄像头拍摄的图像
     * @return 图像的Bitmap
     */
    public static Bitmap getBitMap(byte[] data, Rect roi, int angle, boolean isBackCamera) {
        YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, width, height), 80,
                byteArrayOutputStream);
        byte[] jpegData = byteArrayOutputStream.toByteArray();
        // 获取照相后的bitmap
        Bitmap tmpBitmap = BitmapFactory.decodeByteArray(jpegData, 0,
                jpegData.length);
        Matrix matrix = new Matrix();
        matrix.reset();
        matrix.preRotate(angle);
        if (!isBackCamera) {
            matrix.preScale(-1.0f, 1.0f);
        }
        tmpBitmap = Bitmap.createBitmap(tmpBitmap, roi.left, roi.top, roi.width(),
                roi.height(), matrix, true);
        tmpBitmap = tmpBitmap.copy(Bitmap.Config.ARGB_8888, true);

//		int hight = tmpBitmap.getHeight() > tmpBitmap.getWidth() ? tmpBitmap
//				.getHeight() : tmpBitmap.getWidth();
//
//		float scale = hight / 800.0f;
//
//		if (scale > 1) {
//			tmpBitmap = Bitmap.createScaledBitmap(tmpBitmap,
//					(int) (tmpBitmap.getWidth() / scale),
//					(int) (tmpBitmap.getHeight() / scale), false);
//		}
        return tmpBitmap;
    }

    public static void printMemoryInfo(String tag, Context context) {
//		Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
//		Debug.getMemoryInfo(memoryInfo);
//		Log.e(tag, "printMemoryInfo: " + memoryInfo.nativePss);

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        Debug.MemoryInfo[] memoryInfos = activityManager.getProcessMemoryInfo(new int[]{Process.myPid()});

        for (Debug.MemoryInfo memoryInfo : memoryInfos) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                int testMem = Integer.parseInt(memoryInfo.getMemoryStat("summary.native-heap")) +
                        Integer.parseInt(memoryInfo.getMemoryStat("summary.code")) +
                        Integer.parseInt(memoryInfo.getMemoryStat("summary.stack")) +
                        Integer.parseInt(memoryInfo.getMemoryStat("summary.private-other")) +
                        Integer.parseInt(memoryInfo.getMemoryStat("summary.system"));
                Log.e(tag, "testMem by getMemoryStat: " + testMem);
            } else {
                Log.e(tag, "testMem: " + (memoryInfo.getTotalPss() - memoryInfo.dalvikPss));
            }
        }

    }

    public static String saveFeature(Context context, byte[] feature, String path, String name) {
        File dir = new File(Environment.getExternalStorageDirectory().getPath(), path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) return null;
        }
        File file = new File(dir, name);
        FileOutputStream fos = null;
        String ret = null;
        try {
            fos = new FileOutputStream(file);
            fos.write(feature);

            ret = file.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return ret;
    }

    public static byte[] readRawFile(Context context, int id) {
        InputStream stream = context.getResources().openRawResource(id);
        byte[] fileData = new byte[0];
        try {
            int fileSize = stream.available();
            fileData = new byte[fileSize];
            int re = stream.read(fileData);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return fileData;
    }

    public static int getRawFileSize(Context context, int resId) {
        InputStream inputStream = null;
        int fileSize = 0;
        try{
            inputStream = context.getResources().openRawResource(resId);
            fileSize = inputStream.available();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(inputStream != null) {
                try{
                    inputStream.close();
                }catch(IOException e){
                }
            }
        }
        return fileSize;
    }
}

