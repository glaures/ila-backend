package sandbox27.ila.infrastructure.images;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.api.ApiResponse;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sandbox27.ila.infrastructure.error.ServiceException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CloudinaryService {

    @Value("${cloudinary.url}")
    String cloudinaryUrl;
    @Value("${cloudinary.avatar.uploadPreset}")
    String uploadPreset;
    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        cloudinary = new Cloudinary(this.cloudinaryUrl);
    }

    public String upload(String url) throws IOException {
        BufferedInputStream in = new BufferedInputStream(new URL(url).openStream());
        File tempFile = File.createTempFile("avatar_" + url.hashCode(), ".img");
        tempFile.deleteOnExit();
        FileOutputStream fileOutputStream = new FileOutputStream(tempFile);
        byte[] dataBuffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = in.read(dataBuffer, 0, 1024)) != -1) {
            fileOutputStream.write(dataBuffer, 0, bytesRead);
        }
        fileOutputStream.close();
        String filename = tempFile.getCanonicalPath();
        Map res = cloudinary.uploader().unsignedUpload(filename, uploadPreset, ObjectUtils.emptyMap());
        String publicId = (String) res.get("public_id");
        return publicId;
    }

    public String getImageUrl(String name, int width, int... height) {
        Transformation t = new Transformation()
                .width(width)
                .crop("fill");
        if (height.length > 0)
            t.height(height[0]);
        return cloudinary.url().transformation(t)
                .secure(true)
                .generate(name);
    }

    public List<ImageInfo> getAllImagesInFolder(String folder) {
        try {
            ApiResponse listResources = cloudinary.search()
                    .expression("resource_type:image AND folder:" + folder).execute();
            List<Map> resources = (List<Map>) listResources.get("resources");
            return resources.stream()
                    .map(e -> new ImageInfo((String) e.get("public_id"), (int) (e.get("width")), (int) (e.get("height"))))
                    .collect(Collectors.toList());
        } catch (Throwable t) {
            throw new ServiceException(t.getLocalizedMessage());
        }
    }
}
