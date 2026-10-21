package com.akivnah.services.repository;

import com.akivnah.services.model.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, Long> {
    List<ServiceItem> findByActiveTrueOrderByNameAsc();
}
