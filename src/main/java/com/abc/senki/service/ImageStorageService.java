package com.abc.senki.service;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Component
@Service
public interface ImageStorageService {
    boolean isImageFile(MultipartFile file);

    String saveAvatar(MultipartFile file, String fileName);

    String saveLogo(MultipartFile file, String fileName);

    String saveProductImg(MultipartFile file, String fileName);

    String saveImgProduct(MultipartFile file, String fileName);

    void destroyProductImg(UUID fileName);

    String saveProductRatingImg(MultipartFile file, String fileName);
}
