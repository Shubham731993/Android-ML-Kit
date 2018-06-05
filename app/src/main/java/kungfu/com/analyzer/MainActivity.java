package kungfu.com.analyzer;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.FirebaseVisionFace;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions;
import com.google.firebase.ml.vision.face.FirebaseVisionFaceLandmark;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {

  private static final int PICK_IMAGE = 101;
  private ImageView image;
  private TextView detectedTextView;
  private RelativeLayout detectedLayout;
  private double thresholdForEyes = 0.3;
  private double thresholdForSmile = 0.5;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    FirebaseApp.initializeApp(getApplicationContext());
    setContentView(R.layout.activity_main);
    Button selectImage = findViewById(R.id.select_button);
    detectedTextView = findViewById(R.id.detected_text);
    detectedLayout = findViewById(R.id.detected_layout);
    image = findViewById(R.id.image);
    selectImage.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
      }
    });

  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == PICK_IMAGE && resultCode == RESULT_OK) {
      //TODO: action
      if (data == null || (data.getData() == null)) {
        return;
      }
      Uri uri = data.getData();
      Bitmap bitmap = null;
      if (uri != null) {
        detectedLayout.setVisibility(View.GONE);
        image.setImageURI(uri);
        bitmap = compressImage(uri);
      }
      runFaceDetector(uri, bitmap);
    }
  }

  private Bitmap compressImage(Uri uri) {
    Bitmap bmp= null;
    try {
      bmp = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
    } catch (Exception e) {
      showErrorDialog();
    }
    return bmp;
  }

  private void runFaceDetector(Uri uri, Bitmap bitmap) {
    try {
      FirebaseVisionFaceDetectorOptions options =
          new FirebaseVisionFaceDetectorOptions.Builder()
              .setModeType(FirebaseVisionFaceDetectorOptions.ACCURATE_MODE)
              .setLandmarkType(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
              .setClassificationType(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
              .setMinFaceSize(0.15f)
              .setTrackingEnabled(true)
              .build();

      FirebaseVisionImage firebaseVisionImage = null;
      if (bitmap != null) {
        firebaseVisionImage = FirebaseVisionImage.fromBitmap(bitmap);
      } else {
        firebaseVisionImage = FirebaseVisionImage.fromFilePath(this, uri);
      }

      FirebaseVisionFaceDetector detector = FirebaseVision.getInstance().getVisionFaceDetector(options);

      Task<List<FirebaseVisionFace>> result =
          detector.detectInImage(firebaseVisionImage)
              .addOnSuccessListener(
                  new OnSuccessListener<List<FirebaseVisionFace>>() {
                    @Override
                    public void onSuccess(List<FirebaseVisionFace> faces) {
                      // Task completed successfully
                      // ...
                      getInfo(faces);
                    }
                  })
              .addOnFailureListener(
                  new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                      // Task failed with an exception
                      // ...
                      Log.i("Shubham", "Error");
                    }
                  });
    } catch (IOException e) {
      showErrorDialog();
    }


  }

  private void getInfo(List<FirebaseVisionFace> faces) {
    detectedTextView.setText("");
    detectedLayout.setVisibility(View.VISIBLE);
    if (faces.size() > 0) {
      StringBuilder finalDetected = new StringBuilder();
      int counter = 1;
      for (FirebaseVisionFace face : faces) {
        StringBuilder detectedText = new StringBuilder();
        Rect bounds = face.getBoundingBox();
        float rotY = face.getHeadEulerAngleY();  // Head is rotated to the right rotY degrees
        float rotZ = face.getHeadEulerAngleZ();  // Head is tilted sideways rotZ degrees

        // If landmark detection was enabled (mouth, ears, eyes, cheeks, and
        // nose available):
        FirebaseVisionFaceLandmark leftEar = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EAR);
        if (leftEar != null) {
          FirebaseVisionPoint leftEarPos = leftEar.getPosition();
        }
        float rightEyeOpenProb = 0, leftEyeOpenProb = 0, smileProb;

        // If classification was enabled:
        if (face.getSmilingProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
          smileProb = face.getSmilingProbability();
          if (smileProb > thresholdForSmile) {
            detectedText.append("Person ").append(counter).append(" is smiling ");
          } else {
            detectedText.append("Person ").append(counter).append(" is not smiling ");
          }
        }
        if (detectedText.length() > 0) {
          if (face.getRightEyeOpenProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
            rightEyeOpenProb = face.getRightEyeOpenProbability();
          }
          if (face.getLeftEyeOpenProbability() != FirebaseVisionFace.UNCOMPUTED_PROBABILITY) {
            leftEyeOpenProb = face.getRightEyeOpenProbability();
          }
          if (rightEyeOpenProb > thresholdForEyes && leftEyeOpenProb > thresholdForEyes) {
            detectedText.append("with both eyes open");
          } else if (rightEyeOpenProb > thresholdForEyes) {
            detectedText.append("with right eye open");
          } else if (leftEyeOpenProb > thresholdForEyes) {
            detectedText.append("with left eye open");
          } else {
            detectedText.append("with both eyes closed");
          }

          // If face tracking was enabled:
          if (face.getTrackingId() != FirebaseVisionFace.INVALID_ID) {
            int id = face.getTrackingId();
          }
        } else {
          detectedText.append("Person ").append(counter).append(" smile cannot be computed");
        }
        finalDetected.append(detectedText).append("\n");
        counter++;
      }
      detectedTextView.setText(finalDetected.toString());
    } else {
      detectedTextView.setText("No Faces Detected");
    }

  }

  public String getPathFromURI(Uri contentUri) {
    String res = null;
    String[] proj = {MediaStore.Images.Media.DATA};
    Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
    if (cursor.moveToFirst()) {
      int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
      res = cursor.getString(column_index);
    }
    cursor.close();
    return res;
  }

  public void showErrorDialog() {
    Toast.makeText(this, "Something went wrong", Toast.LENGTH_SHORT).show();
  }
}
