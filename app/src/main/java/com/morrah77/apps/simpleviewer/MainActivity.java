package com.morrah77.apps.simpleviewer;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.*;

public class MainActivity extends AppCompatActivity {

    //TODO(h.lazar) split this class to some smaller ones
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    protected static final int REQUEST_GET_IMAGE = 1;
    protected TessBaseAPI tessApi;
    private File currentImageFile = null;
    private String tessDataPath = "";
    String lang;
    Bitmap img;
    String recognized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //TODO(h.lazar) add an ability to set up recognition language manually or from system settings
        lang = "eng";
        fillTextView(getLocalizedString(R.string.app_greeting));
        setupTessApi();
    }

    public void recognizeButtonAction(View v) {
        if (!deviceHasACamera(this)) {
            recognizeStubImage();
            return;
        }
        launchGetImageIntentIfPossible();
    }

    public void recognizeImage() {
        tessApi.setImage(img);
        setLabelProcess();
        recognized = tessApi.getUTF8Text();
        setLabelSuccess();
        fillTextView(recognized);
    }

    public void recognizeStubImage() {
        this.img = BitmapFactory.decodeResource(getResources(), R.drawable.stub);
        ImageView iView = (ImageView) findViewById(R.id.imageView);
        iView.setImageBitmap(this.img);
        recognizeImage();
    }

    void fillTextView(String text) {
        TextView tView = (TextView) findViewById(R.id.texRecognized);
        tView.setText(text);
    }

    private void setLabelProcess() {
        fillLabel(getString(R.string.recognize_label_process));
    }

    private void setLabelSuccess() {
        fillLabel(getString(R.string.recognize_label_success));
    }

    private void setLabelFail() {
        fillLabel(getString(R.string.recognize_label_fail));
    }

    private void fillLabel(String text) {
        TextView tView = (TextView) findViewById(R.id.texRecognizedLabel);
        tView.setText(text);
    }

    String getLocalizedString(int stringsId) {
        //TODO(h.lazar) localize it!
        return getString(stringsId);
    }

    protected void setupTessApi() {
        try {
            this.ensureTessDataFileExists();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        this.tessApi = new TessBaseAPI();
        try {
            //TODO(h.lazar) find a way to pass asset stream instead of file path to TessBaseApi
            this.tessApi.init(this.tessDataPath, this.lang, 0);
        } catch (Exception e) {
            e.printStackTrace();
            fillTextView(e.toString());
        }

    }

    protected BitmapFactory.Options getDecodingOptions() {
        // TODO(h.lazar) calculate these max size values in getMaxRecognizeableImageSize() method
        int targetW = 1000;
        int targetH = 1000;

        BitmapFactory.Options bmOptions = new BitmapFactory.Options();

        // Get the dimensions of the bitmap doing not allocate memory for bitmap entry
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(currentImageFile.getAbsolutePath(), bmOptions);

        // Determine how much to scale down the image
        int scaleFactor = Math.min(bmOptions.outWidth/targetW, bmOptions.outHeight/targetH);

        // Decode the image file into a Bitmap sized to fit max recognizeable image size
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        return bmOptions;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == REQUEST_GET_IMAGE && resultCode == RESULT_OK) {
            if (currentImageFile != null) {
                try {
                    img = BitmapFactory.decodeFile(currentImageFile.getAbsolutePath(), getDecodingOptions());
                    ImageView iView = (ImageView) findViewById(R.id.imageView);
                    iView.setImageBitmap(img);
                } catch (Exception e){
                    Log.e("ERROR", "Coud not process captured image", e);
                    setLabelFail();
                    return;

                }
                recognizeImage();
            } else {
                Log.e("ERROR", "currentImageFile is null");
                setLabelFail();
            }
        } else {
            Log.e("ERROR", "Invalid request code or result code " + requestCode + ", " + resultCode);
            setLabelFail();
        }
    }

    private File getCurrentImageFile() throws IOException {
        File imagesPath = new File(getFilesDir(), "images");
        if (!imagesPath.exists()) {
            imagesPath.mkdirs();
        }
        File imageFile = File.createTempFile("currentImage", ".jpg", imagesPath);
        return imageFile;
    }

    private void launchGetImageIntentIfPossible() {
        Intent getImageIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (getImageIntent.resolveActivity(getPackageManager()) != null) {
            currentImageFile = null;
            try {
                currentImageFile = getCurrentImageFile();
            } catch (IOException e) {
                Log.e("ERROR", "Could not get currentImageFile", e);
                setLabelFail();
            }
            if (currentImageFile != null) {
                Uri currentImageUri = FileProvider.getUriForFile(this,
                        getString(R.string.app_authorities),
                        currentImageFile);
                getImageIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentImageUri);
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP) {
                    getImageIntent.setClipData(ClipData.newRawUri("", currentImageUri));
                    getImageIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivityForResult(getImageIntent, REQUEST_GET_IMAGE);
            }
        } else {
            Log.w("WARN", "No available camera application on device!");
            recognizeStubImage();
        }
    }

    private boolean deviceHasACamera(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }

    protected void ensureTessDataFileExists() throws Exception {
        String fileName = lang + ".traineddata";
        String assetFilePath = "tessdata/" + fileName;
        this.tessDataPath = getFilesDir().toString()+ "/";
        File cat = new File(this.tessDataPath + "tessdata");
        if(!cat.exists() && !cat.mkdirs()) {
            throw new FileNotFoundException();
        }
        String dataFilePath = this.tessDataPath + assetFilePath;
        File file = new File(dataFilePath);
//        file.delete();
//        file = new File(dataFilePath);
        //TODO(h.lazar) to avoid this crunchy copying find a way to pass asset stream instead of file to TessBaseApi or rewrite that API
        if (!file.exists()) {
            AssetManager assetMgr = getAssets();
            String[] lst = assetMgr.list("tessdata");
            InputStream assetStream = assetMgr.open(assetFilePath);
            OutputStream fileStream = new FileOutputStream(dataFilePath);

            byte[] buffer = new byte[1024];
            int read;
            while ((read = assetStream.read(buffer)) != -1) {
                fileStream.write(buffer, 0, read);
            }


            fileStream.flush();
            fileStream.close();
            assetStream.close();

            file = new File(dataFilePath);
            if (!file.exists()) {
                throw new FileNotFoundException();
            }
        }
    }

    /**
     * A native method that is implemented by the 'native-lib' native library,
     * which is packaged with this application.
     */
//    public native String stringFromJNI();
}
