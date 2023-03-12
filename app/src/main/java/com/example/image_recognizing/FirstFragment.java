package com.example.image_recognizing;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.camera.core.Camera;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.navigation.fragment.NavHostFragment;

import com.example.image_recognizing.databinding.FragmentFirstBinding;
import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.label.TensorLabel;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class FirstFragment extends Fragment {

    private FragmentFirstBinding binding;

    private static final String TAG = "MainActivity";
    private static final String MODEL_PATH = "model.tflite";
    private static final String IMAGE_PATH = "1.png";
    private static final String LABELS_PATH = "labels.txt";
    private static final int IMAGE_MEAN = 0;
    private static final float IMAGE_STD = 1.0f;

    private Interpreter mInterpreter;
    private List<String> mLabels;

    private ImageView mImageView;
    private TextView mResultTextView;

    private PreviewView previewView;

    private ImageCapture imageCapture;
    private Boolean flag = true;
    private Executor executor = Executors.newSingleThreadExecutor();

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {

        binding = FragmentFirstBinding.inflate(inflater, container, false);
        return binding.getRoot();

    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mImageView = binding.imageView;
        mResultTextView = binding.resultText;
        previewView = binding.previewView;
        startCamera();

        binding.buttonFirst.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                NavHostFragment.findNavController(FirstFragment.this)
//                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
                if(flag) {
                    takePhoto();
                }else {
                    mImageView.setVisibility(View.GONE);
                    previewView.setVisibility(View.VISIBLE);
                    binding.buttonFirst.setText("Recognition");
                }
                flag = !flag;
            }
        });
    }
    private void startCamera() {
        // Create a new instance of the Camera object
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this.getContext());

        cameraProviderFuture.addListener(() -> {
            try {
                // Get the camera provider and bind the preview use case to the preview view
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                int rotation = getActivity().getWindowManager().getDefaultDisplay().getRotation();

                Preview preview = new Preview.Builder().setTargetRotation(rotation).build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Set up the image capture use case
                imageCapture = new ImageCapture.Builder().build();

                // Create a new camera selector to choose the back camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                        .build();

                // Bind the use cases to the camera with lifecycle
                Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, imageCapture, preview);

            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this.getContext()));
    }

    private ByteBuffer convertBitmapToByteBuffer(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int channels = 3;

        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(channels * width * height * 4);
        byteBuffer.order(ByteOrder.nativeOrder());

        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);

        int pixel = 0;
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                int pixelValue = pixels[pixel++];

                byteBuffer.putFloat(((pixelValue >> 16) & 0xFF) / IMAGE_STD);
                byteBuffer.putFloat(((pixelValue >> 8) & 0xFF) / IMAGE_STD);
                byteBuffer.putFloat((pixelValue & 0xFF) / IMAGE_STD);
            }
        }

        return byteBuffer;
    }

    private Bitmap loadBitmapFromAsset(String fileName) {
        try {
            InputStream inputStream = this.getActivity().getAssets().open(fileName);
            return BitmapFactory.decodeStream(inputStream);
        } catch (IOException e) {
            Log.e(TAG, "Failed to load bitmap from asset", e);
            return null;
        }
    }

    private List<String> loadLabels() throws IOException {
        List<String> labels = new ArrayList<>();
        InputStream inputStream = this.getActivity().getAssets().open(LABELS_PATH);
        byte[] buffer = new byte[inputStream.available()];
        inputStream.read(buffer);
        inputStream.close();
        String[] labelArray = new String(buffer, "UTF-8").split("\n");
        Collections.addAll(labels, labelArray);
        return labels;
    }

    private void loadImage() {
        try {
            InputStream stream = this.getActivity().getAssets().open(IMAGE_PATH);
            Drawable drawable = Drawable.createFromStream(stream, null);
            mImageView.setImageDrawable(drawable);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
    private String getCurrentDateTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String currentDateAndTime = sdf.format(new Date());
        return currentDateAndTime;
    }
    private void takePhoto() {
        String filename = getCurrentDateTime()+".jpg";
        File file = new File(this.getActivity().getExternalFilesDir(null), filename);
        ImageCapture.OutputFileOptions outputFileOptions = new ImageCapture.OutputFileOptions.Builder(file).build();
//        imageCapture = new ImageCapture.Builder()
//                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
//                .build();
        imageCapture.takePicture(outputFileOptions, executor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // Image saved successfully
                try {
                    AssetFileDescriptor assetFileDescriptor = FirstFragment.this.getActivity().getAssets().openFd(MODEL_PATH);
                    FileInputStream inputStream = new FileInputStream(assetFileDescriptor.getFileDescriptor());
                    FileChannel fileChannel = inputStream.getChannel();
                    long startOffset = assetFileDescriptor.getStartOffset();
                    long declaredLength = assetFileDescriptor.getDeclaredLength();
                    MappedByteBuffer modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
                    mInterpreter = new Interpreter(modelBuffer, new Interpreter.Options());
                    mLabels = loadLabels();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to load model or labels", e);
                }
                FirstFragment.this.getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String imagePath = FirstFragment.this.getActivity().getExternalFilesDir(null) + "/" + filename;
                        File imageFile = new File(imagePath);
                        Bitmap bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
                        mImageView.setImageBitmap(bitmap);
                        mImageView.setVisibility(View.VISIBLE);
                        previewView.setVisibility(View.GONE);
                        binding.buttonFirst.setText("Again");
//                        bitmap = loadBitmapFromAsset(IMAGE_PATH);
                        predict(bitmap);
                    }
                });
//                Bitmap bitmap = loadBitmapFromAsset(IMAGE_PATH);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.d("capture_error", exception.getMessage());
                // Error occurred while saving image
            }
        });
    }


    private void predict(Bitmap bitmap) {
        TensorImage inputImageBuffer = new TensorImage(DataType.FLOAT32);
        inputImageBuffer.load(bitmap);
        ImageProcessor imageProcessor =
                new ImageProcessor.Builder()
                        .add(new ResizeOp(100, 100, ResizeOp.ResizeMethod.BILINEAR))
                        .build();
        inputImageBuffer = imageProcessor.process(inputImageBuffer);

        TensorBuffer outputProbabilityBuffer =
                TensorBuffer.createFixedSize(new int[]{1, mLabels.size()}, DataType.FLOAT32);

        mInterpreter.run(inputImageBuffer.getBuffer(), outputProbabilityBuffer.getBuffer().rewind());

        Map<String, Float> labeledProbability =
                new TensorLabel(mLabels, outputProbabilityBuffer)
                        .getMapWithFloatValue();

        String result = "";
        float highestProbability = 0.0f;
        for (Map.Entry<String, Float> entry : labeledProbability.entrySet()) {
            String label = entry.getKey();
            float probability = entry.getValue();
            result += String.format("%s: %f\n", label, probability);
            if (probability > highestProbability) {
                highestProbability = probability;
            }
        }

        final float threshold = 0.5f;
        if (highestProbability > threshold) {
            final String prediction = labeledProbability.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .orElse(null).getKey();
            mResultTextView.setText(prediction);
        } else {
            mResultTextView.setText("Unknown");
        }

        Log.d(TAG, result);
    }



    @Override
    public void onDestroyView() {
        if (mInterpreter != null) {
            mInterpreter.close();
        }
        super.onDestroyView();
        binding = null;
    }

}