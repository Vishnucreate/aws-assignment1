import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;

import java.io.*;
import java.net.URL;
import java.util.List;

public class TextRecognition {
    private static final String SQS_QUEUE_URL = "https://sqs.us-east-1.amazonaws.com/323052225972/sqsforcarimage";  // Your SQS queue URL
    private static final String S3_BUCKET_URL = "https://njit-cs-643.s3.us-east-1.amazonaws.com";  // Public S3 bucket URL

    public static void main(String[] args) {
        SqsClient sqsClient = SqsClient.builder().credentialsProvider(ProfileCredentialsProvider.create()).build();
        RekognitionClient rekognitionClient = RekognitionClient.builder().build();

        boolean keepProcessing = true;

        while (keepProcessing) {
            // Receive messages from the SQS queue
            ReceiveMessageRequest receiveRequest = ReceiveMessageRequest.builder()
                    .queueUrl(SQS_QUEUE_URL)
                    .maxNumberOfMessages(10)
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

                // Download the image from the public S3 bucket
                String imageUrl = S3_BUCKET_URL + "/" + imageIndex + ".jpg";  // Assuming images are named as 1.jpg, 2.jpg, etc.
                try {
                    downloadImage(imageUrl, imageIndex + ".jpg");

                    // Call Rekognition for text detection
                    DetectTextRequest detectTextRequest = DetectTextRequest.builder()
                            .image(Image.builder().s3Object(software.amazon.awssdk.services.rekognition.model.S3Object.builder()
                                    .bucket("njit-cs-643")
                                    .name(imageIndex + ".jpg")
                                    .build()).build())
                            .build();

                    DetectTextResponse detectTextResponse = rekognitionClient.detectText(detectTextRequest);

                    // Process the text detection results
                    List<TextDetection> textDetections = detectTextResponse.textDetections();
                    System.out.println("Detected text for image " + imageIndex + ".jpg:");
                    for (TextDetection text : textDetections) {
                        System.out.println("Detected: " + text.detectedText() + " (Confidence: " + text.confidence() + ")");
                    }
                } catch (IOException e) {
                    System.err.println("Failed to download image: " + imageIndex + ".jpg");
                    e.printStackTrace();
                }
            }
        }
    }

    // Method to download the image from the public S3 bucket URL
    private static void downloadImage(String imageUrl, String outputFileName) throws IOException {
        try (InputStream in = new URL(imageUrl).openStream()) {
            Files.copy(in, Paths.get(outputFileName));
            System.out.println("Image downloaded: " + outputFileName);
        }
    }
}
