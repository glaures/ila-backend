package sandbox27.ila.infrastructure.images;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ImageInfo {

    String publicId;
    int height;
    int width;

    public ImageInfo(String publicId, int width, int height) {
        this.publicId = publicId;
        this.height = height;
        this.width = width;
    }

    public float getAspectRatio(){
        return ((float)this.width) / this.height;
    }
}
