package com.akivnah.services.config;

import com.akivnah.services.model.Role;
import com.akivnah.services.model.ServiceItem;
import com.akivnah.services.model.UserAccount;
import com.akivnah.services.repository.ServiceItemRepository;
import com.akivnah.services.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
public class DataInitializer {

    @Bean
    CommandLineRunner seed(UserRepository users, ServiceItemRepository services, PasswordEncoder encoder,
                           @Value("${akivnah.admin.username}") String adminUser,
                           @Value("${akivnah.admin.email}") String adminEmail,
                           @Value("${akivnah.admin.password}") String adminPassword) {
        return args -> {
            if (!users.existsByUsername(adminUser)) {
                UserAccount admin = new UserAccount();
                admin.setUsername(adminUser);
                admin.setEmail(adminEmail);
                admin.setPassword(encoder.encode(adminPassword));
                admin.setRole(Role.ADMIN);
                users.save(admin);
            }
            if (services.count() == 0) {
                services.save(item("Software Development", "Custom web and mobile applications built to your requirements.", "50000"));
                services.save(item("IT Consulting", "Architecture, technology roadmaps and digital transformation advice.", "25000"));
                services.save(item("Cloud & DevOps Support", "Cloud migration, CI/CD pipelines and 24x7 monitoring.", "30000"));
                services.save(item("Payroll & HR Solutions", "Payroll processing, offer letters, payslips and compliance support.", "15000"));
            }
        };
    }

    private ServiceItem item(String name, String description, String price) {
        ServiceItem s = new ServiceItem();
        s.setName(name);
        s.setDescription(description);
        s.setPrice(new BigDecimal(price));
        return s;
    }
}
