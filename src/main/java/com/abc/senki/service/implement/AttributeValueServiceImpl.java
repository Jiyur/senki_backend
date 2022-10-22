package com.abc.senki.service.implement;

import com.abc.senki.model.entity.AttributeValueEntity;
import com.abc.senki.repositories.AttributeValueRepository;
import com.abc.senki.service.AttributeValueService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class AttributeValueServiceImpl implements AttributeValueService {
    @Autowired
    AttributeValueRepository attributeValueRepository;
    @Override
    public void saveValue(AttributeValueEntity value) {
        attributeValueRepository.save(value);
    }
}