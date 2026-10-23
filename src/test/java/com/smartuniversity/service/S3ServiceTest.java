package com.smartuniversity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** fileExists must say "gone" only when S3 itself says the file isn't there. */
class S3ServiceTest {

    private final S3Client client = mock(S3Client.class);
    private S3Service service;

    @BeforeEach
    void setUp() {
        service = spy(new S3Service());
        doReturn(client).when(service).getS3Client();
    }

    @Test
    void aFileThatIsThereExists() {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder().build());

        assertTrue(service.fileExists("chatbot/notes.pdf"));
    }

    @Test
    void aMissingKeyDoesNotExist() {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(NoSuchKeyException.builder().message("gone").build());

        assertFalse(service.fileExists("chatbot/notes.pdf"));
    }

    @Test
    void aNotFoundResponseDoesNotExist() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(404).message("Not Found").build());

        assertFalse(service.fileExists("chatbot/notes.pdf"));
    }

    @Test
    void anyOtherFailureIsReportedRatherThanTreatedAsGone() {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenThrow(S3Exception.builder().statusCode(403).message("Access Denied").build());

        assertThrows(S3Exception.class, () -> service.fileExists("chatbot/notes.pdf"));
    }
}
