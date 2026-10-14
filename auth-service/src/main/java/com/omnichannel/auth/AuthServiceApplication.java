package com.omnichannel.auth;

import com.omnichannel.auth.entity.Role;
import com.omnichannel.auth.entity.User;
import com.omnichannel.auth.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedUsers(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            String[][] userDefs = {
                {"admin@ecommerce.com", "Admin@123", "Hệ Thống Admin (Quản Trị)", "0987654321", "ADMIN"},
                {"customer@ecommerce.com", "Customer@123", "Nguyễn Văn A (Khách Hàng Thân Thiết)", "0912345678", "CUSTOMER"},
                {"customerB@ecommerce.com", "Customer@123", "Trần Thị Bích (Khách Hàng VIP)", "0988888888", "CUSTOMER"},
                {"le.van.cuong@gmail.com", "Customer@123", "Lê Văn Cường", "0901234567", "CUSTOMER"},
                {"pham.thi.dung@gmail.com", "Customer@123", "Phạm Thị Dung", "0912345679", "CUSTOMER"},
                {"hoang.duc.em@gmail.com", "Customer@123", "Hoàng Đức Em", "0923456780", "CUSTOMER"},
                {"vu.thi.giang@gmail.com", "Customer@123", "Vũ Thị Giang", "0934567891", "CUSTOMER"},
                {"dang.minh.hai@gmail.com", "Customer@123", "Đặng Minh Hải", "0945678902", "CUSTOMER"},
                {"bui.thanh.in@gmail.com", "Customer@123", "Bùi Thanh In", "0956789013", "CUSTOMER"},
                {"ngo.quoc.khanh@gmail.com", "Customer@123", "Ngô Quốc Khánh", "0967890124", "CUSTOMER"}
            };

            for (String[] def : userDefs) {
                if (!userRepository.existsByEmail(def[0])) {
                    User u = new User();
                    u.setEmail(def[0]);
                    u.setPassword(passwordEncoder.encode(def[1]));
                    u.setFullName(def[2]);
                    u.setPhone(def[3]);
                    u.setRole(Role.valueOf(def[4]));
                    u.setActive(true);
                    userRepository.save(u);
                }
            }
            System.out.println(">>> [AuthService] Seeded default users (10 accounts available)!");
        };
    }
}
