import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.util.List;

public class TextRecognition {
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/323052225972/sqsforcarimage";  // Update with your SQS queue URL
    private static final String S3_BUCKET_NAME = "caimagestorage";  // Replace with your S3 bucket name

    public static void main(String[] args) {
        SqsClient sqsClient = SqsClient.builder().credentialsProvider(ProfileCredentialsProvider.create()).build();
        S3Client s3Client = S3Client.builder().credentialsProvider(ProfileCredentialsProvider.create()).build();
        RekognitionClient rekognitionClient = RekognitionClient.builder().credentialsProvider(ProfileCredentialsProvider.create()).build();

        boolean keepProcessing = true;

        while (keepProcessing) {
            // Receive messages from the SQS queue
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(SQS_QUEUE_URL)
                    .maxNumberOfMessages(10)  // Adjust as necessary
                    .waitTimeSeconds(20)
                    .build();

            ReceiveMessageResponse receiveResponse = sqsClient.receiveMessage(receiveRequest);
            List<Message> messages = receiveResponse.messages();

            for (Message message : messages) {
                String imageIndex = message.body();

                // Check for termination signal (-1)
                if (imageIndex.equals("-1")) {
                    System.out.println("No more images to process. Exiting...");
                    keepProcessing = false;
                    break;
                }

                // Download the image from S3
                String imageKey = imageIndex + ".jpg";  // Assuming the images are named as 1.jpg, 2.jpg, etc.
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                        .bucket(S3_BUCKET_NAME)
                        .key(imageKey)
                        .build();

                InputStream imageStream = s3Client.getObject(getObjectRequest);

                // Call Rekognition for text detection
                DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                        .image(Image.builder().s3Object(software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                                .bucket(S3_BUCKET_NAME)
                                .name(imageKey)
                                .build()).build())
                        .build();

                DetectTextResponse detectTextResponse = rekognitionClient.detectText(detectTextRequest);

                // Process the text detection results
                List<TextDetection> textDetections = detectTextResponse.textDetections();
                System.out.println("Detected text for image " + imageKey + ":");
                for (TextDetection text : textDetections) {
                    System.out.println("Detected: " + text.detectedText() + " (Confidence: " + text.confidence() + ")");
                }
            }
        }
    }
}
