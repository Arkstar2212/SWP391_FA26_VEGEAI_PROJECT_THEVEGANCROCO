package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.community.repository.VideoRepository;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "app.community.seed-defaults", havingValue = "true")
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class CommunityDataInitializer implements CommandLineRunner {

    private final BlogPostRepository blogPostRepository;
    private final VideoRepository videoRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        Optional<User> adminOpt = userRepository.findByUsernameOrEmailIgnoreCase("admin");
        Optional<User> memberOpt = userRepository.findByUsernameOrEmailIgnoreCase("member");
        if (adminOpt.isEmpty()) {
            return;
        }
        User admin = adminOpt.get();
        User member = memberOpt.orElse(admin);

        if (blogPostRepository.count() == 0) {
            log.info("[COMMUNITY-INIT] Khởi tạo bài đăng Cộng đồng (Blog & Video Bếp Chay)...");
            BlogPost p1 = new BlogPost();
            p1.setAuthor(admin);
            p1.setTitle("Bí quyết nấu Phở Chay Nấm Hương ngọt thanh tự nhiên kèm Video hướng dẫn");
            p1.setExcerpt("Chia sẻ công thức nấu nước dùng phở chay từ củ cải trắng, mía lau, táo đỏ và nấm hương khô cực kỳ đậm đà không cần mì chính.");
            p1.setContent("""
                    Nước dùng phở chay ngon chuẩn vị phụ thuộc vào sự kết hợp hài hòa giữa vị ngọt từ rau củ nướng và hương thơm của thảo mộc.

                    1. Nguyên liệu chuẩn bị:
                    - Nấm hương khô: 100g, nấm đùi gà: 200g, đậu hũ ky tươi: 150g.
                    - Củ cải trắng, cà rốt, bắp Mỹ, 2 khúc mía lau nướng thơm.
                    - Gia vị thảo mộc: hoa hồi, thảo quả, quế chi, đinh hương, gừng nướng.

                    2. Cách chế biến:
                    - Nướng gừng, hành boa-rô và mía lau cho dậy mùi thơm, sau đó hầm cùng củ cải trắng và bắp Mỹ trong 45 phút.
                    - Rang sơ hoa hồi, quế, thảo quả rồi cho vào túi vải thả vào nồi nước dùng 15 phút cuối.
                    - Nêm muối hồng Himalaya, hạt nêm nấm hương và chút nước tương Tamari cho tròn vị.
                    """);
            p1.setCoverImage("https://images.unsplash.com/photo-1547592180-85f173990554?w=900");
            p1.setVideoUrl("https://www.youtube.com/watch?v=fbddv3vaYJ0");
            p1.setCategory(BlogCategory.KITCHEN);
            p1.setStatus(BlogStatus.PUBLISHED);
            p1.setAiModerationStatus("AI_PASSED");
            p1.setAiSafetyScore(98);
            p1.setAiModerationNotes("✅ AI Kiểm duyệt đạt chuẩn (98/100): Bài chia sẻ công thức & video bếp chay hợp lệ, 100% nguyên liệu thực vật.");
            p1.setReviewer(admin);
            p1.setSubmittedAt(LocalDateTime.now().minusDays(2));
            p1.setReviewedAt(LocalDateTime.now().minusDays(2));
            p1.setPublishedAt(LocalDateTime.now().minusDays(2));

            BlogPost p2 = new BlogPost();
            p2.setAuthor(member);
            p2.setTitle("Cân bằng Đạm thực vật, Sắt và Vitamin B12 cho người ăn thuần chay");
            p2.setExcerpt("Làm thế nào để xây dựng thực đơn tuần đủ 100% axit amin thiết yếu từ đậu phụ, hạt quinoa, đậu gà và rau lá xanh đậm.");
            p2.setContent("""
                    Nhiều người mới bắt đầu ăn thuần chay thường lo lắng về việc thiếu hụt đạm (Protein), sắt và Vitamin B12. Thực tế, bạn hoàn toàn có thể đạt chỉ số sức khỏe tối ưu nhờ nguyên tắc kết hợp thực phẩm thông minh:

                    1. Phối hợp Đậu hạt + Ngũ cốc nguyên cám:
                    - Kết hợp cơm gạo lứt cùng đậu đen, đậu lăng hoặc đậu gà giúp cung cấp đầy đủ 9 axit amin thiết yếu.

                    2. Tối ưu hấp thu Sắt thực vật (Non-heme Iron):
                    - Luôn bổ sung nguồn giàu Vitamin C (ớt chuông, cam, chanh, cà chua) trong bữa ăn có rau bina, cải bó xôi hoặc rong biển để tăng khả năng hấp thu sắt gấp 3 lần.

                    3. Sử dụng AI Bếp Trưởng Thông Minh VEGEAI:
                    - Sau khi tính BMI & TDEE tại mục Dinh dưỡng, bạn có thể chuyển thẳng mục tiêu sang AI Bếp Trưởng để lập thực đơn tuần/tháng tự động cân đối dưỡng chất!
                    """);
            p2.setCoverImage("https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=900");
            p2.setCategory(BlogCategory.NUTRITION);
            p2.setStatus(BlogStatus.PUBLISHED);
            p2.setAiModerationStatus("AI_PASSED");
            p2.setAiSafetyScore(97);
            p2.setAiModerationNotes("✅ AI Kiểm duyệt đạt chuẩn (97/100): Nội dung dinh dưỡng thuần chay khoa học, không vi phạm nguyên tắc cộng đồng.");
            p2.setReviewer(admin);
            p2.setSubmittedAt(LocalDateTime.now().minusDays(1));
            p2.setReviewedAt(LocalDateTime.now().minusDays(1));
            p2.setPublishedAt(LocalDateTime.now().minusDays(1));

            BlogPost p3 = new BlogPost();
            p3.setAuthor(member);
            p3.setTitle("Mẹo quét tủ lạnh thông minh: Biến rau củ cuối tuần thành 3 món chay ngon");
            p3.setExcerpt("Chia sẻ trải nghiệm chụp ảnh ngăn mát tủ lạnh trên AI Bếp Trưởng Thông Minh để nhận diện độ tươi và lên thực đơn chống lãng phí thực phẩm.");
            p3.setContent("""
                    Cuối tuần tủ lạnh thường còn dư nhiều loại rau củ lẻ như cà chua, nấm bào ngư, cải ngọt và vài bìa đậu phụ non. Thay vì đau đầu nghĩ món ăn, mình chụp ngay một bức ảnh trong khung chat AI Bếp Trưởng Thông Minh:

                    - AI tự động nhận diện từng nguyên liệu và chấm điểm độ tươi (cà chua hơi chín được ưu tiên nấu trước trong 24h).
                    - Chỉ cần bấm nút 'Đưa nguyên liệu vào AI lập thực đơn', hệ thống tự động lên ngay danh sách món ăn chay phù hợp với khẩu vị và loại trừ dị ứng đậu phộng của mình!
                    """);
            p3.setCoverImage("https://images.unsplash.com/photo-1540420773420-3366772f4999?w=900");
            p3.setCategory(BlogCategory.GREEN_LIVING);
            p3.setStatus(BlogStatus.PUBLISHED);
            p3.setAiModerationStatus("AI_PASSED");
            p3.setAiSafetyScore(96);
            p3.setAiModerationNotes("✅ AI Kiểm duyệt đạt chuẩn (96/100): Bài chia sẻ lối sống xanh tích cực, sẵn sàng hiển thị công khai.");
            p3.setReviewer(admin);
            p3.setSubmittedAt(LocalDateTime.now().minusHours(12));
            p3.setReviewedAt(LocalDateTime.now().minusHours(10));
            p3.setPublishedAt(LocalDateTime.now().minusHours(10));

            blogPostRepository.saveAll(List.of(p1, p2, p3));
        }

        if (videoRepository.count() == 0) {
            log.info("[COMMUNITY-INIT] Khởi tạo Video Bếp Chay mẫu...");
            CommunityVideo v1 = new CommunityVideo();
            v1.setTitle("Cách làm Đậu Hũ Kho Nấm Đông Cô chuẩn vị cơm nhà");
            v1.setDescription("Hướng dẫn chi tiết cách kho đậu hũ non cùng nấm đông cô tươi mềm thấm vị, giàu đạm thực vật.");
            v1.setVideoUrl("https://www.youtube.com/watch?v=fbddv3vaYJ0");
            v1.setThumbnailUrl("https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=800");
            v1.setCategory("MON_CHAY");
            v1.setDurationSeconds(420);
            v1.setStatus(VideoStatus.PUBLISHED);
            v1.setCreatedBy(admin);

            CommunityVideo v2 = new CommunityVideo();
            v2.setTitle("Thực đơn 3 món chay thanh đạm giải nhiệt ngày hè trong 20 phút");
            v2.setDescription("Canh rong biển đậu non, nấm đùi gà áp chảo sốt mè rang và salad bơ tươi.");
            v2.setVideoUrl("https://www.youtube.com/watch?v=2L3b6R5y1wE");
            v2.setThumbnailUrl("https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=800");
            v2.setCategory("DINH_DUONG");
            v2.setDurationSeconds(540);
            v2.setStatus(VideoStatus.PUBLISHED);
            v2.setCreatedBy(admin);

            videoRepository.saveAll(List.of(v1, v2));
        }
    }
}
