package com.vegeai.backend.modules.community.service;

import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.repository.BlogCommentRepository;
import com.vegeai.backend.modules.community.repository.BlogPostRepository;
import com.vegeai.backend.modules.community.repository.QuestionRepository;
import com.vegeai.backend.modules.community.repository.VideoCommentRepository;
import com.vegeai.backend.modules.community.repository.VideoRepository;
import com.vegeai.backend.modules.fridge.entity.FreshnessStatus;
import com.vegeai.backend.modules.fridge.entity.FridgeItem;
import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import com.vegeai.backend.modules.fridge.repository.FridgeItemRepository;
import com.vegeai.backend.modules.notification.entity.Notification;
import com.vegeai.backend.modules.notification.repository.NotificationRepository;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class CommunityDataInitializer implements CommandLineRunner {

    private final VideoRepository videoRepository;
    private final BlogPostRepository blogPostRepository;
    private final BlogCommentRepository blogCommentRepository;
    private final VideoCommentRepository videoCommentRepository;
    private final QuestionRepository questionRepository;
    private final FridgeItemRepository fridgeItemRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void run(String... args) {
        User admin = userRepository.findByEmailIgnoreCase("admin@vegeai.com")
                .or(() -> userRepository.findByUsernameIgnoreCase("admin"))
                .orElse(null);

        if (admin == null) {
            log.warn("[COMMUNITY-INIT] Admin user not found. Skipping community seeds.");
            return;
        }

        User annhien = userRepository.findByUsernameIgnoreCase("annhien").orElse(admin);
        User minhkhoa = userRepository.findByUsernameIgnoreCase("minhkhoa").orElse(admin);
        User thaolinh = userRepository.findByUsernameIgnoreCase("thaolinh").orElse(admin);
        User tuankhang = userRepository.findByUsernameIgnoreCase("tuankhang").orElse(admin);
        User member = userRepository.findByUsernameIgnoreCase("member").orElse(admin);

        // 1. Seed Videos
        if (videoRepository.count() == 0) {
            log.info("[COMMUNITY-INIT] Seeding authentic culinary YouTube videos...");
            seedVideos(admin);
        }

        // 2. Seed Blog Posts (Diverse Authors with Image or Video attached)
        if (blogPostRepository.count() < 5) {
            log.info("[COMMUNITY-INIT] Seeding community blog posts across specialized accounts...");
            seedBlogPosts(admin, annhien, minhkhoa, thaolinh, tuankhang, member);
        }

        // 3. Seed Video Comments
        if (videoCommentRepository.count() == 0) {
            log.info("[COMMUNITY-INIT] Seeding interactive community video comments...");
            seedVideoComments(annhien, minhkhoa, thaolinh, tuankhang, member);
        }

        // 4. Seed Q&A Forum Questions
        if (questionRepository.count() == 0) {
            log.info("[COMMUNITY-INIT] Seeding Q&A forum questions and medical/expert answers...");
            seedQuestions(tuankhang, thaolinh, member, minhkhoa, annhien, admin);
        }

        // 5. Seed Smart Fridge Items for Member
        if (member != null && fridgeItemRepository.countByUserIdAndStatus(member.getUserId(), FridgeItemStatus.ACTIVE) == 0) {
            log.info("[COMMUNITY-INIT] Seeding sample smart fridge inventory for Member account...");
            seedFridgeItems(member);
        }

        // 6. Seed In-App Notifications for Member
        if (member != null && notificationRepository.countByUserIdAndReadAtIsNull(member.getUserId()) == 0) {
            log.info("[COMMUNITY-INIT] Seeding sample in-app notifications for Member account...");
            seedNotifications(member);
        }
    }

    private void seedVideos(User author) {
        List<CommunityVideo> videos = List.of(
                createVideo(
                        "Cơm chiên mì gói chay giòn rụm",
                        "Cách tận dụng mì gói chay và cơm nguội để làm món cơm chiên giòn rụm, thơm ngon khó cưỡng chỉ trong 15 phút. Video có kèm chi tiết gia vị nước tương và đậu phụ non.",
                        "https://www.youtube.com/watch?v=ao_tEpz6niU",
                        "https://img.youtube.com/vi/ao_tEpz6niU/hqdefault.jpg",
                        "KITCHEN",
                        420,
                        author
                ),
                createVideo(
                        "Sữa đặc hạt điều thuần chay béo ngậy tại nhà",
                        "Tự làm sữa đặc thuần chay từ hạt điều tươi ngâm mềm và nước cốt dừa tự nhiên, không chất bảo quản, béo ngậy thơm ngon thích hợp pha cà phê hoặc ăn bánh mì.",
                        "https://www.youtube.com/watch?v=wTqClpgLWWs",
                        "https://img.youtube.com/vi/wTqClpgLWWs/hqdefault.jpg",
                        "NUTRITION",
                        380,
                        author
                ),
                createVideo(
                        "Bún lứt xào rau củ thanh vị ngũ sắc",
                        "Món bún gạo lứt xào cùng nấm đùi gà baby, ớt chuông, đậu cô ve và cà rốt giòn ngọt. Cung cấp trọn vẹn chất xơ, vitamin và khoáng chất cho bữa trưa nhanh gọn.",
                        "https://www.youtube.com/watch?v=22rmN4AYkkk",
                        "https://img.youtube.com/vi/22rmN4AYkkk/hqdefault.jpg",
                        "KITCHEN",
                        490,
                        author
                ),
                createVideo(
                        "Canh chua rau củ thanh mát giải nhiệt mùa hè",
                        "Bí quyết nấu nồi canh chua thuần chay chuẩn vị miền Nam với dứa, cà chua chín, đậu bắp, nấm bào ngư và dọc mùng giòn sần sật.",
                        "https://www.youtube.com/watch?v=9gG--xBmSa8",
                        "https://img.youtube.com/vi/9gG--xBmSa8/hqdefault.jpg",
                        "KITCHEN",
                        530,
                        author
                ),
                createVideo(
                        "Đậu khìa nước dừa đậm đà đưa cơm",
                        "Đậu hũ chiên vàng giòn khìa nước dừa xiêm béo ngọt, thấm đượm gia vị tiêu gừng, màu cánh gián tự nhiên tuyệt đẹp cho bữa cơm gia đình.",
                        "https://www.youtube.com/watch?v=JqMTUKuL2_U",
                        "https://img.youtube.com/vi/JqMTUKuL2_U/hqdefault.jpg",
                        "KITCHEN",
                        460,
                        author
                )
        );

        videoRepository.saveAll(videos);
        log.info("[COMMUNITY-INIT] Successfully seeded {} community videos.", videos.size());
    }

    private CommunityVideo createVideo(String title, String description, String url, String thumb, String category, int duration, User author) {
        CommunityVideo v = new CommunityVideo();
        v.setTitle(title);
        v.setDescription(description);
        v.setVideoUrl(url);
        v.setThumbnailUrl(thumb);
        v.setCategory(category);
        v.setDurationSeconds(duration);
        v.setStatus(VideoStatus.PUBLISHED);
        v.setCreatedBy(author);
        return v;
    }

    private void seedBlogPosts(User admin, User annhien, User minhkhoa, User thaolinh, User tuankhang, User member) {
        List<BlogPost> posts = new ArrayList<>();

        // Post 1 (Admin) - Photo cover
        BlogPost p1 = createPost(admin,
                "Hành trình 30 ngày chuyển đổi sang thuần chay cùng gia đình nhỏ",
                "Những thay đổi kỳ diệu về năng lượng, tiêu hóa và tinh thần khi cả nhà bắt đầu làm bạn với ẩm thực xanh từ rau củ thiên nhiên.",
                """
                Ăn chay không đơn thuần là việc cắt giảm thịt cá, mà là một lối sống quay về với tự nhiên và lắng nghe cơ thể mình.
                
                Những ngày đầu, gia đình tôi từng bỡ ngỡ không biết nấu gì ngoài đậu phụ rán và rau luộc. Nhờ áp dụng công nghệ gợi ý món từ tủ lạnh của VEGEAI, việc lên thực đơn trở nên hào hứng hơn bao giờ hết. Chúng tôi khám phá ra vô vàn loại nấm, hạt dinh dưỡng và cách kết hợp gia vị thảo mộc để bữa cơm vừa đậm đà, vừa thanh nhẹ.
                
                Sau 30 ngày, điều rõ rệt nhất là giấc ngủ sâu hơn, năng lượng buổi sáng tràn đầy và tâm trạng của các thành viên đều nhẹ nhõm, vui vẻ hơn rất nhiều.
                """,
                BlogCategory.GREEN_LIVING, "/images/vietnamese-vegan-table.png", null, 5.0, 3, LocalDateTime.now().minusDays(5));
        posts.add(p1);

        // Post 2 (An Nhiên) - Video attached
        BlogPost p2 = createPost(annhien,
                "Nghệ thuật kho tộ chay: Bí quyết màu cánh gián từ mật mía & nước dừa xiêm",
                "Không cần phẩm màu hay đường cháy đắng, vị ngọt sâu tự nhiên của nước dừa xiêm kết hợp tiêu lốt rừng sẽ nâng tầm món nấm kho tộ lên hàng nghệ thuật.",
                """
                Một nồi nấm kho tộ thơm nức mũi vào ngày mưa là ký ức êm đềm nhất trong gian bếp của tôi.
                
                Để miếng đậu hũ khuôn và nấm đùi gà thấm đều gia vị mà không bị nát, bí quyết nằm ở 3 bước:
                1. Chiên vàng giòn 4 mặt đậu hũ non với lửa vừa, sau đó ngâm ngay vào nước đá lạnh 2 phút để lớp vỏ dai giòn, bên trong mềm béo như kem.
                2. Nước màu: Đun nhỏ lửa mật mía hữu cơ cùng một thìa dầu mè nguyên chất và vài lát gừng đập dập. Khi hỗn hợp sủi bọt tăm màu hổ phách, lập tức trút nấm vào đảo nhanh.
                3. Nước kho: Dùng hoàn toàn 100% nước dừa xiêm tươi, hạ nhỏ lửa ninh liu riu trong 20 phút cho nước sánh keo lại. Rắc thêm hạt tiêu sọ và ớt chỉ thiên cắt lát.
                
                Món này dùng kèm cơm gạo lứt dẻo nóng và một đĩa rau luộc thập cẩm thì ngon khó tả!
                """,
                BlogCategory.KITCHEN, "/images/vegan-tofu-meal.png", "https://www.youtube.com/watch?v=JqMTUKuL2_U", 4.9, 5, LocalDateTime.now().minusDays(4));
        posts.add(p2);

        // Post 3 (Minh Khoa) - Video attached
        BlogPost p3 = createPost(minhkhoa,
                "Thực đơn thuần chay 120g Protein/ngày: Gymer tăng cơ không cần whey động vật",
                "Phá bỏ định kiến ăn chay thiếu chất và teo cơ. Khám phá tỷ lệ vàng kết hợp giữa đậu gà, men dinh dưỡng (nutritional yeast), hạt gai dầu và tempeh.",
                """
                Rất nhiều bạn tập thể hình hỏi tôi: 'Không ăn ức gà và lòng trắng trứng thì lấy đâu ra đạm để phát triển cơ bắp?'
                
                Sự thật là protein thực vật cực kỳ phong phú và không chứa cholesterol xấu hay dư lượng kháng sinh. Đây là cấu trúc phân bổ 120g protein thuần chay mỗi ngày của tôi:
                - Bữa sáng: Yến mạch nấu sữa đậu nành hữu cơ, 2 thìa hạt chia và 30g hạt gai dầu (hemp seeds) (~28g Protein).
                - Bữa trưa: 150g Tempeh áp chảo sốt tương tamari, 1 bát cơm quinoa và bông cải xanh hấp (~36g Protein).
                - Bữa phụ chiều: Sinh tố bơ đậu phộng, bột đậu gà rang và chuối chín (~22g Protein).
                - Bữa tối: Canh đậu hũ non nấu nấm đùi gà, salad đậu lăng đỏ và hạt óc chó (~34g Protein).
                
                Tổng năng lượng đạt chuẩn 2,200 kcal với tỷ lệ amino acid hoàn chỉnh, giúp cơ bắp phục hồi nhanh chóng và không bao giờ bị đầy bụng!
                """,
                BlogCategory.NUTRITION, "/images/fresh-vegetables.png", "https://www.youtube.com/watch?v=wTqClpgLWWs", 5.0, 4, LocalDateTime.now().minusDays(3));
        posts.add(p3);

        // Post 4 (Dr. Thảo Linh) - Photo cover
        BlogPost p4 = createPost(thaolinh,
                "Hiểu đúng về vitamin B12 và sắt hữu cơ: Lời khuyên từ bác sĩ dinh dưỡng",
                "Tại sao người ăn chay trường không nên lo lắng về thiếu máu nếu biết cách kết hợp vitamin C tự nhiên cùng các loại rau củ lá xanh đậm.",
                """
                Dưới góc độ y khoa lâm sàng, sắt thực vật (sắt non-heme) có cơ chế hấp thu nhạy cảm hơn sắt heme từ động vật. Tuy nhiên, tự nhiên luôn có sự bù trừ kỳ diệu:
                
                1. Sức mạnh của Vitamin C: Khi bạn ăn các thực phẩm giàu sắt như rau chân vịt, cải xoăn, đậu lăng kết hợp với nguồn vitamin C (dứa, ớt chuông, nước cốt chanh), tỷ lệ hấp thu sắt tăng lên từ 3 đến 6 lần!
                2. Tránh chất cản trở: Không nên uống trà đặc hoặc cà phê ngay trong hoặc sau bữa ăn chính 1 tiếng, vì tannin và polyphenol sẽ kết tủa sắt làm giảm hấp thu.
                3. Bổ sung Vitamin B12: Người ăn thuần chay lâu năm nên bổ sung vitamin B12 qua men dinh dưỡng (nutritional yeast) hoặc thực phẩm tăng cường, giúp bảo vệ bao myelin thần kinh và sản sinh hồng cầu khỏe mạnh.
                
                Ăn chay đúng khoa học là chìa khóa vàng cho một trái tim khỏe và huyết áp ổn định suốt đời.
                """,
                BlogCategory.NUTRITION, "/images/vietnamese-vegan-table.png", null, 4.9, 4, LocalDateTime.now().minusDays(2));
        posts.add(p4);

        // Post 5 (Tuấn Khang) - Video attached
        BlogPost p5 = createPost(tuankhang,
                "Bí kíp đi chợ sinh viên: Chỉ 150.000đ cho 3 ngày ăn chay no nê và đủ chất",
                "Chia sẻ kinh nghiệm săn rau tươi chợ chiều, bảo quản đậu hũ và cách chế biến món một nồi (one-pot) siêu tiết kiệm cho sinh viên.",
                """
                Là sinh viên năm 3 tự nấu ăn tại phòng trọ, mình nhận ra ăn chay tự nấu vừa rẻ vừa sạch hơn cơm tiệm gấp nhiều lần.
                
                Bảng chi tiêu 150k cho 3 ngày của mình gồm:
                - 4 bìa đậu hũ khuôn nóng mua sáng sớm ở chợ: 20.000đ
                - 1 bắp cải trắng + 1 bó cải ngọt + nửa ký cà chua: 35.000đ
                - 300g nấm rơm tươi + 1 túi nấm kim châm: 45.000đ
                - 1 quả bí đỏ hồ lô nhỏ (nấu được 3 bữa canh): 15.000đ
                - 1 túi đậu phộng sống rang giòn giã muối mè: 20.000đ
                - Hành ngò gia vị: 15.000đ
                
                Món tủ của mình là 'Cơm bí đỏ hầm nấm hạt sen một nồi'. Chỉ cần cho gạo lứt, bí đỏ cắt hạt lựu và nấm vào nồi cơm điện bấm cook, 25 phút sau có ngay bữa ăn thơm lừng, dẻo ngọt mà dọn rửa cực kỳ nhanh gọn!
                """,
                BlogCategory.GREEN_LIVING, "/images/vegan-tofu-meal.png", "https://www.youtube.com/watch?v=ao_tEpz6niU", 4.8, 3, LocalDateTime.now().minusDays(1));
        posts.add(p5);

        // Post 6 (Admin FLAGGED post to showcase AI review queue)
        BlogPost postFlagged = new BlogPost();
        postFlagged.setAuthor(admin);
        postFlagged.setTitle("Cách nấu canh gà hầm sâm bổ lượng cho người ăn chay");
        postFlagged.setExcerpt("Hướng dẫn bồi bổ sức khỏe bằng thịt gà ác hầm sâm và kỷ tử cho người bắt đầu ăn chay.");
        postFlagged.setContent("""
                Đây là bài viết chia sẻ cách dùng thịt gà để bồi bổ cho những ai mới ăn chay thấy mệt mỏi...
                Nguyên liệu gồm có nửa con gà ác, sâm, táo đỏ và kỷ tử...
                """);
        postFlagged.setCategory(BlogCategory.KITCHEN);
        postFlagged.setStatus(BlogStatus.PENDING_REVIEW);
        postFlagged.setAiModerationStatus(com.vegeai.backend.modules.community.entity.AiModerationStatus.FLAGGED);
        postFlagged.setAiModerationReason("[NON_VEGETARIAN] Phát hiện từ khóa thịt động vật 'gà ác' trong nội dung dành cho nền tảng ăn chay VEGEAI. Cần quản trị viên kiểm tra và xử lý thủ công.");
        postFlagged.setSubmittedAt(LocalDateTime.now().minusHours(2));
        posts.add(postFlagged);

        blogPostRepository.saveAll(posts);
        log.info("[COMMUNITY-INIT] Successfully seeded {} rich blog posts.", posts.size());

        // Seed Comments & 5-Star Ratings on blog posts
        seedBlogComments(p1, p2, p3, p4, p5, annhien, minhkhoa, thaolinh, tuankhang, member);
    }

    private BlogPost createPost(User author, String title, String excerpt, String content,
                                BlogCategory category, String coverImage, String videoUrl,
                                Double ratingAvg, Integer ratingCount, LocalDateTime publishedAt) {
        BlogPost post = new BlogPost();
        post.setAuthor(author);
        post.setTitle(title);
        post.setExcerpt(excerpt);
        post.setContent(content);
        post.setCategory(category);
        post.setCoverImage(coverImage);
        post.setVideoUrl(videoUrl);
        post.setRatingAverage(ratingAvg);
        post.setRatingCount(ratingCount);
        post.setStatus(BlogStatus.PUBLISHED);
        post.setAiModerationStatus(com.vegeai.backend.modules.community.entity.AiModerationStatus.SAFE);
        post.setAiModerationReason("Kiểm duyệt tự động đạt chuẩn an toàn thực vật, nội dung tích cực.");
        post.setPublishedAt(publishedAt);
        return post;
    }

    private void seedBlogComments(BlogPost p1, BlogPost p2, BlogPost p3, BlogPost p4, BlogPost p5,
                                  User annhien, User minhkhoa, User thaolinh, User tuankhang, User member) {
        List<BlogComment> comments = new ArrayList<>();

        // Comments on Post 2 (Nghệ thuật kho tộ chay by An Nhiên)
        comments.add(createBlogComment(p2, thaolinh, 5, "Công thức kho tộ dùng nước dừa xiêm tự nhiên này thanh ngọt cực kỳ, không hề bị gắt họng như dùng nước hàng công nghiệp. Rất đáng thử!"));
        comments.add(createBlogComment(p2, tuankhang, 5, "Em làm theo hướng dẫn ngâm đậu vào nước đá sau khi chiên, miếng đậu giòn dai mà bên trong mềm tan béo ngậy luôn chị An ơi! 10/10"));
        comments.add(createBlogComment(p2, member, 5, "Món này kho cùng nấm đùi gà và tiêu xanh đưa cơm kinh khủng. Cảm ơn chị An Nhiên đã chia sẻ công thức tuyệt vời này ạ!"));

        // Comments on Post 3 (120g Protein Gymer by Minh Khoa)
        comments.add(createBlogComment(p3, member, 5, "Bài viết rất khoa học! Mình áp dụng theo công thức bữa sáng yến mạch + hạt chia + hạt gai dầu thấy no lâu và tràn đầy năng lượng cả buổi sáng."));
        comments.add(createBlogComment(p3, annhien, 5, "Tempeh áp chảo sốt tương tamari là món tủ của em, kết hợp với đậu lăng đỏ thì vừa đậm đà vừa đủ axit amin thiết yếu."));
        comments.add(createBlogComment(p3, thaolinh, 5, "Cách phân bổ tỷ lệ protein thực vật rất hợp lý, tránh được việc dồn quá nhiều đạm vào 1 bữa gây gánh nặng cho thận."));

        // Comments on Post 4 (Vitamin B12 & Sắt by Dr. Thảo Linh)
        comments.add(createBlogComment(p4, minhkhoa, 5, "Kiến thức về việc kết hợp vitamin C tự nhiên cùng sắt non-heme rất hữu ích cho các vận động viên thể thao thuần chay. Cảm ơn bác sĩ Linh!"));
        comments.add(createBlogComment(p4, tuankhang, 5, "Trước giờ em hay vừa ăn cơm vừa uống trà đá, đọc bài của bác sĩ mới biết làm giảm hấp thu sắt. Em đã bỏ thói quen này rồi ạ!"));

        // Comments on Post 5 (Đi chợ sinh viên 150k by Tuấn Khang)
        comments.add(createBlogComment(p5, member, 5, "Bài viết gần gũi và thực tế quá bạn ơi. Món cơm bí đỏ nấu một nồi công nhận vừa tiện vừa ngon."));
        comments.add(createBlogComment(p5, annhien, 4, "Mẹo hay lắm Khang! Nhớ bảo quản đậu hũ trong tô nước ngập mặt thay nước mỗi ngày để đậu giữ tươi được lâu hơn nhé."));

        blogCommentRepository.saveAll(comments);
        log.info("[COMMUNITY-INIT] Successfully seeded {} blog comments with 5-star ratings.", comments.size());
    }

    private BlogComment createBlogComment(BlogPost post, User author, int rating, String content) {
        BlogComment c = new BlogComment();
        c.setPost(post);
        c.setAuthor(author);
        c.setRating(rating);
        c.setContent(content);
        return c;
    }

    private void seedVideoComments(User annhien, User minhkhoa, User thaolinh, User tuankhang, User member) {
        List<CommunityVideo> videos = videoRepository.findAll();
        if (videos.isEmpty()) return;

        List<VideoComment> comments = new ArrayList<>();

        // Video 0: Cơm chiên mì gói chay
        CommunityVideo v0 = videos.get(0);
        comments.add(createComment(v0, annhien, "Mẹo nhỏ cho các bạn: Rang cơm cho săn hạt trên chảo gang trước rồi mới rưới sốt tương và trộn mì vụn chiên giòn vào, hạt cơm sẽ giòn rụm mà không bị ngấy dầu đâu nha!"));
        comments.add(createComment(v0, tuankhang, "Vừa nấu thử xong trưa nay! Nhanh gọn mà ngon bất ngờ, đúng chân ái cho sinh viên mùa ôn thi luôn ạ. Cảm ơn Bếp trưởng VEGEAI!"));
        comments.add(createComment(v0, member, "Thêm một chút hành boa-rô phi thơm nữa là đỉnh chóp, cả nhà mình ai cũng khen!"));

        // Video 1: Sữa đặc hạt điều
        if (videos.size() > 1) {
            CommunityVideo v1 = videos.get(1);
            comments.add(createComment(v1, thaolinh, "Chất béo trong hạt điều là chất béo không bão hòa đơn (oleic acid) rất tốt cho thành mạch tim. Công thức này thay thế sữa đặc công nghiệp quá hoàn hảo!"));
            comments.add(createComment(v1, minhkhoa, "Dùng sữa hạt điều này pha cà phê cold brew hoặc chấm bánh mì hoa cúc thuần chay thì xuất sắc 10/10!"));
        }

        // Video 2: Bún lứt xào rau củ
        if (videos.size() > 2) {
            CommunityVideo v2 = videos.get(2);
            comments.add(createComment(v2, annhien, "Bún lứt ngâm nước ấm 15 phút rồi vớt ra xóc chút dầu mè trước khi xào thì sợi bún tơi đều, không hề bị gãy vụn đâu nhé cả nhà."));
            comments.add(createComment(v2, member, "Rau củ ngũ sắc vừa giòn ngọt vừa giàu enzyme sống, ăn xong bụng nhẹ tênh!"));
        }

        // Video 3: Canh chua rau củ
        if (videos.size() > 3) {
            CommunityVideo v3 = videos.get(3);
            comments.add(createComment(v3, thaolinh, "Vị chua thanh từ dứa và me cung cấp vitamin C tự nhiên, giúp hấp thu sắt non-heme trong đậu bắp và nấm hiệu quả hơn gấp 3 lần."));
        }

        // Video 4: Đậu khìa nước dừa
        if (videos.size() > 4) {
            CommunityVideo v4 = videos.get(4);
            comments.add(createComment(v4, tuankhang, "Món này ăn với dưa leo giòn rụm và cơm nóng thì bao nhiêu cơm cũng hết sạch nồi!"));
        }

        videoCommentRepository.saveAll(comments);
        log.info("[COMMUNITY-INIT] Successfully seeded {} video comments.", comments.size());
    }

    private VideoComment createComment(CommunityVideo video, User author, String content) {
        VideoComment c = new VideoComment();
        c.setVideo(video);
        c.setAuthor(author);
        c.setContent(content);
        return c;
    }

    private void seedQuestions(User tuankhang, User thaolinh, User member, User minhkhoa, User annhien, User admin) {
        List<CommunityQuestion> questions = new ArrayList<>();

        // Q1: Tuan Khang hỏi -> Thao Linh trả lời
        CommunityQuestion q1 = new CommunityQuestion();
        q1.setAuthor(tuankhang);
        q1.setContent("Chào bác sĩ Linh, em là sinh viên mới tập ăn chay được 2 tuần. Dạo này em hay bị đầy hơi khi ăn nhiều đậu đỏ và đậu đen. Bác sĩ cho em hỏi cách sơ chế như thế nào để tiêu hóa dễ hơn ạ?");
        q1.setStatus(QuestionStatus.APPROVED);
        q1.setAnswer("Chào Tuấn Khang! Khi nấu các loại đậu hạt khô, em hãy ngâm đậu với nước ấm pha một chút muối loãng từ 8-12 tiếng (hoặc qua đêm), sau đó đổ bỏ nước ngâm và rửa lại thật sạch trước khi nấu. Quá trình này giúp phân giải lượng đường oligosaccharide (nguyên nhân chính gây đầy hơi) và kích hoạt enzyme giúp đậu bùi mềm, êm dịu dạ dày hơn rất nhiều nhé!");
        q1.setAnsweredBy(thaolinh);
        q1.setAnsweredAt(LocalDateTime.now().minusDays(1));
        q1.setReviewedBy(admin);
        q1.setReviewedAt(LocalDateTime.now().minusDays(1));
        questions.add(q1);

        // Q2: Member hỏi -> Minh Khoa trả lời
        CommunityQuestion q2 = new CommunityQuestion();
        q2.setAuthor(member);
        q2.setContent("Anh Khoa cho em hỏi, người gầy muốn tăng cân khi ăn thuần chay thì nên bổ sung calo từ nguồn nào tốt nhất mà không bị tích mỡ xấu?");
        q2.setStatus(QuestionStatus.APPROVED);
        q2.setAnswer("Chào bạn! Để tăng cân lành mạnh thuần thực vật, bạn nên tăng mật độ calo trong bữa ăn bằng các loại chất béo tốt: bơ đậu phộng, bơ hạnh nhân, dầu ô liu nguyên chất, quả bơ chín, hạt điều và sữa yến mạch. Uống thêm 1 ly sinh tố chuối - yến mạch - bơ hạt sau bữa chính 1 tiếng sẽ giúp bạn nạp thêm 400-500 kcal dễ dàng mà không làm đầy bụng.");
        q2.setAnsweredBy(minhkhoa);
        q2.setAnsweredAt(LocalDateTime.now().minusHours(18));
        q2.setReviewedBy(admin);
        q2.setReviewedAt(LocalDateTime.now().minusHours(18));
        questions.add(q2);

        // Q3: An Nhien hỏi -> Admin trả lời
        CommunityQuestion q3 = new CommunityQuestion();
        q3.setAuthor(annhien);
        q3.setContent("Ban quản trị VEGEAI cho mình hỏi tính năng AI Quét Tủ Lạnh có hỗ trợ nhận diện các loại nấm rừng tự nhiên và nấm nhập khẩu không?");
        q3.setStatus(QuestionStatus.APPROVED);
        q3.setAnswer("Chào Bếp Chay An Nhiên! AI Vision của VEGEAI hiện đã được huấn luyện nhận diện hơn 30 loài nấm phổ biến (nấm rơm, đùi gà, bào ngư, kim châm, đông cô, nấm tuyết, nấm hương, mộc nhĩ...). Với các loại nấm lạ hoặc nấm hái tự nhiên, AI sẽ gắn nhãn cảnh báo để người dùng cẩn trọng an toàn vệ sinh thực phẩm trước khi chế biến.");
        q3.setAnsweredBy(admin);
        q3.setAnsweredAt(LocalDateTime.now().minusHours(6));
        q3.setReviewedBy(admin);
        q3.setReviewedAt(LocalDateTime.now().minusHours(6));
        questions.add(q3);

        questionRepository.saveAll(questions);
        log.info("[COMMUNITY-INIT] Successfully seeded {} Q&A forum discussions.", questions.size());
    }

    private void seedFridgeItems(User user) {
        LocalDate today = LocalDate.now();
        List<FridgeItem> items = List.of(
                FridgeItem.builder()
                        .userId(user.getUserId())
                        .name("Đậu hũ non hữu cơ")
                        .normalizedName("dau hu non huu co")
                        .category("VEGAN")
                        .isVegetarian(true)
                        .quantity(2.0)
                        .unit("hộp")
                        .freshnessStatus(FreshnessStatus.FRESH)
                        .freshnessScore(95.0)
                        .firstScannedAt(LocalDateTime.now().minusDays(1))
                        .lastScannedAt(LocalDateTime.now())
                        .estimatedShelfLifeDays(5)
                        .expiryDate(today.plusDays(4))
                        .status(FridgeItemStatus.ACTIVE)
                        .storageTip("Ngâm trong nước sạch ngập mặt, bảo quản ngăn mát 4°C và thay nước mỗi ngày.")
                        .notes("Mua tại Chay Xanh Mart Phú Nhuận")
                        .build(),
                FridgeItem.builder()
                        .userId(user.getUserId())
                        .name("Nấm rơm búp tươi")
                        .normalizedName("nam rom bup tuoi")
                        .category("VEGAN")
                        .isVegetarian(true)
                        .quantity(300.0)
                        .unit("g")
                        .freshnessStatus(FreshnessStatus.USABLE)
                        .freshnessScore(70.0)
                        .firstScannedAt(LocalDateTime.now().minusDays(2))
                        .lastScannedAt(LocalDateTime.now())
                        .estimatedShelfLifeDays(3)
                        .expiryDate(today.plusDays(1))
                        .status(FridgeItemStatus.ACTIVE)
                        .storageTip("Nên nấu ngay trong hôm nay hoặc xào sơ cấp đông để giữ độ giòn ngọt.")
                        .notes("Ưu tiên nấu canh hoặc kho tộ trước.")
                        .build(),
                FridgeItem.builder()
                        .userId(user.getUserId())
                        .name("Cải ngọt VietGAP tươi")
                        .normalizedName("cai ngot vietgap tuoi")
                        .category("VEGAN")
                        .isVegetarian(true)
                        .quantity(1.0)
                        .unit("bó")
                        .freshnessStatus(FreshnessStatus.FRESH)
                        .freshnessScore(92.0)
                        .firstScannedAt(LocalDateTime.now().minusDays(1))
                        .lastScannedAt(LocalDateTime.now())
                        .estimatedShelfLifeDays(5)
                        .expiryDate(today.plusDays(3))
                        .status(FridgeItemStatus.ACTIVE)
                        .storageTip("Bọc khăn giấy khô và cho vào túi zip có lỗ thoáng khí trong ngăn rau củ.")
                        .notes("Rau sạch không thuốc bảo vệ thực vật")
                        .build(),
                FridgeItem.builder()
                        .userId(user.getUserId())
                        .name("Bí đỏ hồ lô dẻo")
                        .normalizedName("bi do ho lo deo")
                        .category("VEGAN")
                        .isVegetarian(true)
                        .quantity(1.0)
                        .unit("quả")
                        .freshnessStatus(FreshnessStatus.FRESH)
                        .freshnessScore(98.0)
                        .firstScannedAt(LocalDateTime.now().minusDays(3))
                        .lastScannedAt(LocalDateTime.now())
                        .estimatedShelfLifeDays(20)
                        .expiryDate(today.plusDays(15))
                        .status(FridgeItemStatus.ACTIVE)
                        .storageTip("Để nơi khô ráo thoáng mát, tránh ánh nắng trực tiếp.")
                        .notes("Thích hợp nấu canh kiểm hoặc chè đậu phộng")
                        .build(),
                FridgeItem.builder()
                        .userId(user.getUserId())
                        .name("Cà chua chín mọng Đà Lạt")
                        .normalizedName("ca chua chin mong da lat")
                        .category("VEGAN")
                        .isVegetarian(true)
                        .quantity(500.0)
                        .unit("g")
                        .freshnessStatus(FreshnessStatus.USABLE)
                        .freshnessScore(75.0)
                        .firstScannedAt(LocalDateTime.now().minusDays(3))
                        .lastScannedAt(LocalDateTime.now())
                        .estimatedShelfLifeDays(5)
                        .expiryDate(today.plusDays(2))
                        .status(FridgeItemStatus.ACTIVE)
                        .storageTip("Để cuống hướng lên trên ở nhiệt độ phòng hoặc ngăn mát nếu đã chín mềm.")
                        .notes("Làm sốt cà chua đậu hũ hoặc nấu canh chua")
                        .build()
        );

        fridgeItemRepository.saveAll(items);
        log.info("[COMMUNITY-INIT] Successfully seeded {} fridge items for Member.", items.size());
    }

    private void seedNotifications(User user) {
        List<Notification> notifs = new ArrayList<>();

        Notification n1 = new Notification();
        n1.setUserId(user.getUserId());
        n1.setEventType("FRIDGE_EXPIRY_WARNING");
        n1.setTitle("❄️ Cảnh báo tủ lạnh thông minh");
        n1.setContent("Nấm rơm búp tươi của bạn chỉ còn 1 ngày hạn sử dụng. Hãy nhờ Bếp Trưởng AI hướng dẫn món nấm kho tộ ngay hôm nay nhé!");
        n1.setRedirectUrl("/fridge-inventory");
        n1.setDedupeKey("fridge_expiry_nam_rom_" + LocalDate.now());
        notifs.add(n1);

        Notification n2 = new Notification();
        n2.setUserId(user.getUserId());
        n2.setEventType("STREAK_ACHIEVEMENT");
        n2.setTitle("🎉 Chúc mừng chuỗi ngày ăn xanh!");
        n2.setContent("Bạn đã hoàn thành chuỗi 14 ngày ăn thuần thực vật liên tục. Chỉ số calo và tiêu hóa của bạn đang cải thiện rất tuyệt vời!");
        n2.setRedirectUrl("/profile");
        n2.setDedupeKey("streak_14_days_" + user.getUserId());
        notifs.add(n2);

        Notification n3 = new Notification();
        n3.setUserId(user.getUserId());
        n3.setEventType("COMMUNITY_ANSWER");
        n3.setTitle("💬 Phản hồi từ Chuyên gia dinh dưỡng");
        n3.setContent("HLV Minh Khoa vừa chia sẻ thực đơn tăng cơ 120g Protein thuần thực vật mới trên bảng tin Cộng đồng.");
        n3.setRedirectUrl("/blog");
        n3.setDedupeKey("community_post_minhkhoa_" + user.getUserId());
        notifs.add(n3);

        notificationRepository.saveAll(notifs);
        log.info("[COMMUNITY-INIT] Successfully seeded {} in-app notifications for Member.", notifs.size());
    }
}
