package com.omnichannel.product;

import com.omnichannel.product.entity.Category;
import com.omnichannel.product.entity.Product;
import com.omnichannel.product.repository.CategoryRepository;
import com.omnichannel.product.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;

@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }

    @Bean
    public CommandLineRunner seedCatalog(CategoryRepository categoryRepository, ProductRepository productRepository) {
        return args -> {
            if (categoryRepository.count() == 0) {
                categoryRepository.save(new Category("Điện thoại thông minh", "smartphones", "Điện thoại cao cấp chính hãng"));
                categoryRepository.save(new Category("Laptop & Máy tính", "laptops", "Laptop doanh nhân, gaming"));
                categoryRepository.save(new Category("Tai nghe & Âm thanh", "audio", "Tai nghe chống ồn, loa Bluetooth"));
                categoryRepository.save(new Category("Phụ kiện công nghệ", "accessories", "Sạc, ốp lưng, đồng hồ"));
            }

            saveProduct(productRepository, "iPhone 16 Pro Max 256GB Desert Titanium (Flash Sale)", "IPHONE-16-PM-256", "iphone-16-pro-max-256gb",
                    "Siêu phẩm điện thoại cao cấp nhất của Apple với chip A18 Pro, nút Camera Control và khung viền Titan siêu nhẹ. Dành cho sự kiện Flash Sale chịu tải cao!",
                    new BigDecimal("34990000"), new BigDecimal("37990000"), 10, "smartphones",
                    "https://images.unsplash.com/photo-1695048133142-1a20484d2569?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("19990000"));

            saveProduct(productRepository, "Samsung Galaxy S24 Ultra 512GB Titanium Gray", "SAMS-S24U-512", "samsung-galaxy-s24-ultra",
                    "Galaxy AI quyền năng, camera 200MP zoom quang 5x cùng bút S-Pen tích hợp.",
                    new BigDecimal("31990000"), new BigDecimal("35490000"), 50, "smartphones",
                    "https://images.unsplash.com/photo-1610945415295-d9bbf067e59c?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("27990000"));

            saveProduct(productRepository, "MacBook Pro 14 M3 Pro 18GB/512GB Space Black", "MBP-14-M3PRO", "macbook-pro-14-m3-pro",
                    "Đỉnh cao hiệu năng đồ họa và lập trình với chip Apple M3 Pro, màn hình Liquid Retina XDR 120Hz.",
                    new BigDecimal("49990000"), new BigDecimal("52990000"), 25, "laptops",
                    "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Sony WH-1000XM5 Chống Ồn Đầu Bảng", "SONY-WH1000XM5", "sony-wh-1000xm5",
                    "Chống ồn chủ động hàng đầu ngành, thời lượng pin 30 giờ, âm thanh Hi-Res LDAC.",
                    new BigDecimal("7990000"), new BigDecimal("8990000"), 100, "audio",
                    "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("6490000"));

            saveProduct(productRepository, "Apple Watch Ultra 2 GPS + Cellular 49mm Titanium", "AW-ULTRA-2-49", "apple-watch-ultra-2",
                    "Đồng hồ thể thao bền bỉ nhất, định vị GPS kép chính xác, màn hình 3000 nits.",
                    new BigDecimal("21490000"), new BigDecimal("22990000"), 40, "accessories",
                    "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "iPad Pro M4 11 inch 256GB Wi-Fi Space Black", "IPAD-PRO-M4-11", "ipad-pro-m4-11-inch",
                    "Màn hình OLED Ultra Retina XDR đỉnh cao, độ mỏng chỉ 5.3mm với sức mạnh bứt phá từ chip Apple M4.",
                    new BigDecimal("27990000"), new BigDecimal("29990000"), 35, "laptops",
                    "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("24990000"));

            saveProduct(productRepository, "Bàn Phím Cơ Không Dây Keychron Q1 Pro QMK/VIA Carbon", "KEYCHRON-Q1-PRO", "ban-phim-co-keychron-q1-pro",
                    "Vỏ nhôm CNC nguyên khối, kết nối Bluetooth 5.1 & Type-C, hot-swap toàn bộ switch, hỗ trợ tùy biến QMK/VIA.",
                    new BigDecimal("4590000"), new BigDecimal("4990000"), 60, "accessories",
                    "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Chuột Công Thái Học Logitech MX Master 3S Pale Grey", "LOGI-MX-MASTER-3S", "chuot-logitech-mx-master-3s",
                    "Cảm biến 8000 DPI theo dõi trên mọi bề mặt kể cả mặt kính, nút bấm Quiet Clicks êm ái, cuộn siêu tốc MagSpeed.",
                    new BigDecimal("2490000"), new BigDecimal("2890000"), 80, "accessories",
                    "https://images.unsplash.com/photo-1527864550417-7fd91fc51a46?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("1990000"));

            saveProduct(productRepository, "Loa Di Động Cao Cấp Marshall Stanmore III Bluetooth", "MARSHALL-STANMORE-3", "loa-marshall-stanmore-iii",
                    "Chất âm sống động tràn ngập không gian phòng khách, thiết kế vintage hoài cổ đặc trưng của thương hiệu Marshall.",
                    new BigDecimal("9990000"), new BigDecimal("10990000"), 30, "audio",
                    "https://images.unsplash.com/photo-1545454675-3531b543be5d?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Tai Nghe Apple AirPods Pro 2 USB-C MagSafe", "AIRPODS-PRO-2-USBC", "apple-airpods-pro-2-usbc",
                    "Chống ồn chủ động gấp 2 lần, âm thanh thích ứng tự động điều chỉnh theo môi trường và chuẩn sạc Type-C hiện đại.",
                    new BigDecimal("5990000"), new BigDecimal("6490000"), 75, "audio",
                    "https://images.unsplash.com/photo-1600294037681-c80b4cb5b434?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("4890000"));

            saveProduct(productRepository, "Laptop Gaming Asus ROG Zephyrus G16 OLED Core Ultra 9", "ASUS-ROG-G16-OLED", "asus-rog-zephyrus-g16-oled",
                    "Màn hình OLED 2.5K 240Hz sắc nét đỉnh cao, trang bị Intel Core Ultra 9 185H và card đồ họa NVIDIA RTX 4080.",
                    new BigDecimal("69990000"), new BigDecimal("74990000"), 15, "laptops",
                    "https://images.unsplash.com/photo-1603302576837-37561b2e2302?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Màn Hình Chuyên Đồ Họa Dell UltraSharp 27 4K U2723QE", "DELL-U2723QE-4K", "man-hinh-dell-ultrasharp-27-4k",
                    "Tấm nền IPS Black độ tương phản 2000:1, chuẩn màu 98% DCI-P3, cổng kết nối USB-C Hub truyền dữ liệu và sạc 90W.",
                    new BigDecimal("13990000"), new BigDecimal("15490000"), 45, "accessories",
                    "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Xiaomi 14 Ultra 512GB Leica Photography Kit", "XIAOMI-14-ULTRA", "xiaomi-14-ultra-512gb",
                    "Hệ thống 4 camera quang học Leica cảm biến 1-inch biến thiên khẩu độ, quay video 8K chuyên nghiệp.",
                    new BigDecimal("28990000"), new BigDecimal("32990000"), 20, "smartphones",
                    "https://images.unsplash.com/photo-1598327105666-5b89351aff97?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("23990000"));

            saveProduct(productRepository, "Tay Cầm Chơi Game Không Dây Sony DualSense Edge PS5", "SONY-DUALSENSE-EDGE", "tay-cam-ps5-dualsense-edge",
                    "Tay cầm Pro cao cấp cho game thủ với cần analog có thể thay thế, nút gạt phía sau tùy biến và hành trình cò có thể chỉnh.",
                    new BigDecimal("4990000"), new BigDecimal("5490000"), 50, "accessories",
                    "https://images.unsplash.com/photo-1600080972464-8e5f35f63d08?auto=format&fit=crop&w=800&q=80", false, null);

            // NEW PRODUCTS ADDED FOR NEXUS THEME — 16 ITEMS
            saveProduct(productRepository, "iPhone 15 Pro 256GB", "IP15P-256", "iphone-15-pro-256gb",
                    "iPhone 15 Pro bản 256GB, thiết kế nhẹ, camera 48MP và hiệu năng mạnh mẽ.",
                    new BigDecimal("29990000"), new BigDecimal("32990000"), 30, "smartphones",
                    "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Samsung Galaxy Z Fold6 512GB", "SAM-FOLD6-512", "samsung-galaxy-z-fold6-512gb",
                    "Galaxy Z Fold6 với bản lề nâng cấp, màn hình lớn khi gập mở, phù hợp người dùng multitask.",
                    new BigDecimal("42990000"), new BigDecimal("45990000"), 8, "smartphones",
                    "https://images.unsplash.com/photo-1580910051075-4d4b7f8f5e69?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("38990000"));

            saveProduct(productRepository, "Xiaomi 14T Pro 512GB", "XIAOMI-14T-PRO-512", "xiaomi-14t-pro-512gb",
                    "Xiaomi 14T Pro hiệu năng cao, camera chuyên nghiệp và pin lớn cho tác vụ nặng.",
                    new BigDecimal("16990000"), new BigDecimal("18990000"), 20, "smartphones",
                    "https://images.unsplash.com/photo-1682695795011-1b61b6f9c3d8?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Google Pixel 9 Pro 256GB", "GP9P-256", "google-pixel-9-pro-256gb",
                    "Pixel 9 Pro với camera computational mạnh mẽ và Android gốc tối ưu.",
                    new BigDecimal("19990000"), new BigDecimal("21990000"), 25, "smartphones",
                    "https://images.unsplash.com/photo-1579546929346-1b7b9d4e6f1d?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "MacBook Air M3 13 inch 16GB/512GB", "MBA-M3-13-16-512", "macbook-air-m3-13-16-512",
                    "MacBook Air M3 mỏng nhẹ, RAM 16GB và SSD 512GB, phù hợp cho công việc di động.",
                    new BigDecimal("27990000"), new BigDecimal("30990000"), 18, "laptops",
                    "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "ASUS TUF Gaming A15 RTX 4060", "ASUS-TUF-A15-4060", "asus-tuf-gaming-a15-rtx-4060",
                    "Laptop gaming hiệu năng cao với RTX 4060, tản nhiệt tối ưu cho session chơi game dài.",
                    new BigDecimal("24990000"), new BigDecimal("26990000"), 22, "laptops",
                    "https://images.unsplash.com/photo-1626039364214-1a5e3b9f1f9f?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("21990000"));

            saveProduct(productRepository, "Lenovo Legion 5 RTX 4070", "LEN-L5-4070", "lenovo-legion-5-rtx-4070",
                    "Lenovo Legion 5 trang bị RTX 4070, phù hợp cho creative và gaming.",
                    new BigDecimal("35990000"), new BigDecimal("38990000"), 12, "laptops",
                    "https://images.unsplash.com/photo-1612831455546-46b6b9ed6f0b?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Dell XPS 13", "DELL-XPS-13", "dell-xps-13",
                    "Dell XPS 13 nhỏ gọn, màn hình InfinityEdge và tính di động cao.",
                    new BigDecimal("21990000"), new BigDecimal("23990000"), 16, "laptops",
                    "https://images.unsplash.com/photo-1541807084-5c52b6b3b4f6?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Sony WF-1000XM5", "SONY-WF1000XM5", "sony-wf-1000xm5",
                    "Tai nghe true wireless chống ồn WF-1000XM5, âm thanh chi tiết, phù hợp di chuyển.",
                    new BigDecimal("4990000"), new BigDecimal("5990000"), 50, "audio",
                    "https://images.unsplash.com/photo-1617045999423-7d5a7d2c9c3f?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("3990000"));

            saveProduct(productRepository, "JBL Charge 5", "JBL-CHARGE-5", "jbl-charge-5",
                    "Loa di động JBL Charge 5, pin lâu và âm bass mạnh mẽ.",
                    new BigDecimal("2190000"), new BigDecimal("2490000"), 40, "audio",
                    "https://images.unsplash.com/photo-1585386959984-a4155227c6c4?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Marshall Emberton II", "MARSHALL-EMBER-2", "marshall-emberton-ii",
                    "Loa di động Marshall Emberton II nhỏ gọn, âm trầm sâu và thiết kế cổ điển.",
                    new BigDecimal("2790000"), new BigDecimal("3190000"), 30, "audio",
                    "https://images.unsplash.com/photo-1563201515-79b2b9b2e4c3?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Bose QuietComfort Ultra Headphones", "BOSE-QC-ULTRA", "bose-qc-ultra",
                    "Tai nghe Bose QuietComfort Ultra chống ồn chủ động, âm thanh cân bằng, phù hợp studio di động.",
                    new BigDecimal("6990000"), new BigDecimal("7990000"), 20, "audio",
                    "https://images.unsplash.com/photo-1616628188478-4c6f2e0e6f88?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("5990000"));

            saveProduct(productRepository, "Logitech G Pro X Superlight 2", "LOGI-GPROX2", "logitech-g-pro-x-superlight-2",
                    "Chuột chơi game siêu nhẹ Logitech G Pro X Superlight 2, sensor chính xác cho esports.",
                    new BigDecimal("3490000"), new BigDecimal("3990000"), 60, "accessories",
                    "https://images.unsplash.com/photo-1608603385515-9c3b0f1a1f3a?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Keychron K8 Pro", "KEYCHRON-K8-PRO", "keychron-k8-pro",
                    "Bàn phím Keychron K8 Pro không dây, hot-swap, phù hợp làm việc & chơi game.",
                    new BigDecimal("1990000"), new BigDecimal("2290000"), 70, "accessories",
                    "https://images.unsplash.com/photo-1587829741301-dc798b83add3?auto=format&fit=crop&w=800&q=80", false, null);

            saveProduct(productRepository, "Samsung Galaxy Watch7", "SAM-GW-7", "samsung-galaxy-watch7",
                    "Đồng hồ Galaxy Watch7 với cảm biến sức khỏe nâng cao và thời lượng pin cải thiện.",
                    new BigDecimal("6990000"), new BigDecimal("7990000"), 35, "accessories",
                    "https://images.unsplash.com/photo-1523275335684-37898b6baf30?auto=format&fit=crop&w=800&q=80", true, new BigDecimal("5990000"));

            saveProduct(productRepository, "Anker 737 Power Bank", "ANKER-737", "anker-737-power-bank",
                    "Sạc dự phòng Anker 737 24,000mAh hiệu suất cao, sạc nhanh nhiều thiết bị cùng lúc.",
                    new BigDecimal("1490000"), new BigDecimal("1990000"), 120, "accessories",
                    "https://images.unsplash.com/photo-1555617117-08b6b1b5a0a1?auto=format&fit=crop&w=800&q=80", false, null);

            System.out.println(">>> [ProductService] Seeded catalog with 30 products!");
        };
    }

    private void saveProduct(ProductRepository repo, String name, String sku, String slug,
                             String desc, BigDecimal price, BigDecimal origPrice, int stock,
                             String cat, String img, boolean flash, BigDecimal flashPrice) {
        if (repo.findBySku(sku).isEmpty()) {
            Product p = new Product();
            p.setName(name);
            p.setSku(sku);
            p.setSlug(slug);
            p.setDescription(desc);
            p.setPrice(price);
            p.setOriginalPrice(origPrice);
            p.setStock(stock);
            p.setCategorySlug(cat);
            p.setImageUrl(img);
            p.setFlashSale(flash);
            p.setFlashSalePrice(flashPrice);
            repo.save(p);
        }
    }
}
