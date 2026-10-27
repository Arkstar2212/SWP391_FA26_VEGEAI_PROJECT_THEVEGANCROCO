package com.vegeai.backend.modules.fridge.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vegeai.backend.modules.auth.service.JwtService;
import com.vegeai.backend.modules.auth.repository.EmailVerificationTokenRepository;
import com.vegeai.backend.modules.fridge.dto.*;
import com.vegeai.backend.modules.fridge.entity.FreshnessStatus;
import com.vegeai.backend.modules.fridge.entity.FridgeItem;
import com.vegeai.backend.modules.fridge.entity.FridgeItemStatus;
import com.vegeai.backend.modules.fridge.repository.FridgeItemRepository;
import com.vegeai.backend.modules.user.entity.AccountStatus;
import com.vegeai.backend.modules.user.entity.User;
import com.vegeai.backend.modules.user.entity.UserRole;
import com.vegeai.backend.modules.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FridgeControllerTest {
    @org.springframework.boot.test.mock.mockito.MockBean
    private com.vegeai.backend.modules.community.service.CommunityDataInitializer communityDataInitializer;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private FridgeItemRepository fridgeItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User primaryUser;
    private String primaryToken;

    private User secondaryUser;
    private String secondaryToken;

    @BeforeEach
    void setUp() {
        fridgeItemRepository.deleteAll();
        // Verification tokens reference users created by other test classes.
        emailVerificationTokenRepository.deleteAll();
        userRepository.deleteAll();

        primaryUser = User.builder()
                .username("thanh_tai_chef")
                .email("thanhtai@vegeai.com")
                .passwordHash(passwordEncoder.encode("SecretPass@123"))
                .fullName("Thanh Tài")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .allergies("PEANUTS, SOY")
                .build();
        primaryUser = userRepository.save(primaryUser);
        primaryToken = jwtService.generateAccessToken(primaryUser);

        secondaryUser = User.builder()
                .username("other_user")
                .email("other@vegeai.com")
                .passwordHash(passwordEncoder.encode("SecretPass@123"))
                .fullName("Người dùng khác")
                .role(UserRole.MEMBER)
                .status(AccountStatus.ACTIVE)
                .build();
        secondaryUser = userRepository.save(secondaryUser);
        secondaryToken = jwtService.generateAccessToken(secondaryUser);
    }

    @Test
    @DisplayName("POST /api/fridge/confirm-items: Thêm mới nguyên liệu sau khi quét thành công")
    void confirmScanItems_newItems_success() throws Exception {
        ConfirmFridgeScanRequest request = ConfirmFridgeScanRequest.builder()
                .items(List.of(
                        ConfirmFridgeItemDto.builder()
                                .name("Cải ngọt")
                                .category("VEGAN")
                                .isVegetarian(true)
                                .quantity(1.0)
                                .unit("bó")
                                .freshnessStatus(FreshnessStatus.FRESH)
                                .freshnessScore(0.95)
                                .estimatedShelfLifeDays(5)
                                .storageTip("Bọc báo để ngăn mát")
                                .build(),
                        ConfirmFridgeItemDto.builder()
                                .name("Cà chua")
                                .category("VEGAN")
                                .isVegetarian(true)
                                .quantity(4.0)
                                .unit("quả")
                                .freshnessStatus(FreshnessStatus.FRESH)
                                .freshnessScore(0.9)
                                .estimatedShelfLifeDays(4)
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/fridge/confirm-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalProcessed").value(2))
                .andExpect(jsonPath("$.data.newlyAddedCount").value(2))
                .andExpect(jsonPath("$.data.updatedExistingCount").value(0))
                .andExpect(jsonPath("$.data.items", hasSize(2)))
                .andExpect(jsonPath("$.data.items[0].name").value("Cải ngọt"))
                .andExpect(jsonPath("$.data.items[0].isVegetarian").value(true))
                .andExpect(jsonPath("$.data.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.memoryInsights", hasSize(2)));

        assertEquals(2, fridgeItemRepository.count());
    }

    @Test
    @DisplayName("POST /api/fridge/confirm-items: Quét lại món cũ -> Kích hoạt cơ chế Smart Memory cập nhật vòng đời")
    void confirmScanItems_reScanExisting_updatesLifecycleAndDaysInFridge() throws Exception {
        // Giả lập 3 ngày trước người dùng đã quét Cà chua
        FridgeItem oldTomato = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Cà chua")
                .normalizedName("ca chua")
                .category("VEGAN")
                .isVegetarian(true)
                .quantity(5.0)
                .unit("quả")
                .freshnessStatus(FreshnessStatus.FRESH)
                .freshnessScore(0.95)
                .firstScannedAt(LocalDateTime.now().minusDays(3))
                .lastScannedAt(LocalDateTime.now().minusDays(3))
                .estimatedShelfLifeDays(5)
                .expiryDate(LocalDate.now().plusDays(2))
                .status(FridgeItemStatus.ACTIVE)
                .build();
        fridgeItemRepository.save(oldTomato);

        // Hôm nay quét lại: AI phát hiện Cà chua đã sang USABLE (còn 2 ngày)
        ConfirmFridgeScanRequest request = ConfirmFridgeScanRequest.builder()
                .items(List.of(
                        ConfirmFridgeItemDto.builder()
                                .name("Cà chua")
                                .freshnessStatus(FreshnessStatus.USABLE)
                                .freshnessScore(0.7)
                                .quantity(3.0)
                                .unit("quả")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/api/fridge/confirm-items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.totalProcessed").value(1))
                .andExpect(jsonPath("$.data.newlyAddedCount").value(0))
                .andExpect(jsonPath("$.data.updatedExistingCount").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("Cà chua"))
                .andExpect(jsonPath("$.data.items[0].daysInFridge").value(3))
                .andExpect(jsonPath("$.data.items[0].daysRemaining").value(2))
                .andExpect(jsonPath("$.data.items[0].freshnessStatus").value("USABLE"))
                .andExpect(jsonPath("$.data.items[0].warningMessage", containsString("Đã nằm trong tủ 3 ngày")));

        // Vẫn chỉ có 1 bản ghi trong DB, không bị trùng lặp
        assertEquals(1, fridgeItemRepository.count());
    }

    @Test
    @DisplayName("POST /api/fridge/items: Thêm thủ công nguyên liệu mới thành công")
    void addManualItem_success() throws Exception {
        CreateFridgeItemRequest request = CreateFridgeItemRequest.builder()
                .name("Đậu phụ non")
                .category("VEGAN")
                .isVegetarian(true)
                .quantity(300.0)
                .unit("g")
                .freshnessStatus(FreshnessStatus.FRESH)
                .estimatedShelfLifeDays(3)
                .storageTip("Ngâm nước sạch trong hộp kín")
                .notes("Hộp mua VinMart")
                .build();

        mockMvc.perform(post("/api/fridge/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Đậu phụ non"))
                .andExpect(jsonPath("$.data.quantity").value(300.0))
                .andExpect(jsonPath("$.data.unit").value("g"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        assertEquals(1, fridgeItemRepository.count());
    }

    @Test
    @DisplayName("GET /api/fridge/items: Lấy danh sách nguyên liệu sắp xếp theo hạn dùng tăng dần")
    void getMyFridgeItems_sortedByExpiry() throws Exception {
        // Món 1: Hết hạn sau 5 ngày
        FridgeItem item1 = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Bắp cải")
                .normalizedName("bap cai")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .expiryDate(LocalDate.now().plusDays(5))
                .status(FridgeItemStatus.ACTIVE)
                .build();

        // Món 2: Hết hạn sau 1 ngày (sắp hỏng hơn, phải lên trước)
        FridgeItem item2 = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Nấm rơm")
                .normalizedName("nam rom")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .expiryDate(LocalDate.now().plusDays(1))
                .status(FridgeItemStatus.ACTIVE)
                .build();

        fridgeItemRepository.saveAll(List.of(item1, item2));

        mockMvc.perform(get("/api/fridge/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].name").value("Nấm rơm"))
                .andExpect(jsonPath("$.data[1].name").value("Bắp cải"));
    }

    @Test
    @DisplayName("PUT /api/fridge/items/{id}: Chỉnh sửa thông tin nguyên liệu thành công")
    void updateItem_success() throws Exception {
        FridgeItem item = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Cà chua")
                .normalizedName("ca chua")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .expiryDate(LocalDate.now().plusDays(4))
                .status(FridgeItemStatus.ACTIVE)
                .build();
        item = fridgeItemRepository.save(item);

        UpdateFridgeItemRequest updateReq = UpdateFridgeItemRequest.builder()
                .name("Cà chua bi hữu cơ")
                .quantity(500.0)
                .unit("g")
                .freshnessStatus(FreshnessStatus.USABLE)
                .build();

        mockMvc.perform(put("/api/fridge/items/" + item.getItemId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Cà chua bi hữu cơ"))
                .andExpect(jsonPath("$.data.quantity").value(500.0))
                .andExpect(jsonPath("$.data.freshnessStatus").value("USABLE"));
    }

    @Test
    @DisplayName("PATCH /api/fridge/items/{id}/status: Đánh dấu đã dùng (CONSUMED) hoặc đã vứt (DISCARDED)")
    void updateItemStatus_markConsumed() throws Exception {
        FridgeItem item = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Đậu bắp")
                .normalizedName("dau bap")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .expiryDate(LocalDate.now().plusDays(2))
                .status(FridgeItemStatus.ACTIVE)
                .build();
        item = fridgeItemRepository.save(item);

        UpdateFridgeItemStatusRequest statusReq = UpdateFridgeItemStatusRequest.builder()
                .status(FridgeItemStatus.CONSUMED)
                .build();

        mockMvc.perform(patch("/api/fridge/items/" + item.getItemId() + "/status")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONSUMED"));

        // Kiểm tra danh sách ACTIVE sẽ không còn món này
        mockMvc.perform(get("/api/fridge/items")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/fridge/expiring-soon: Lấy danh sách thực phẩm cần cứu nguy trong 48h")
    void getExpiringSoon_returnsOnlyUrgentItems() throws Exception {
        // Món 1: Sắp hết hạn trong 1 ngày -> Phải nằm trong danh sách cảnh báo
        FridgeItem urgent = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Giá đỗ")
                .normalizedName("gia do")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .expiryDate(LocalDate.now().plusDays(1))
                .status(FridgeItemStatus.ACTIVE)
                .build();

        // Món 2: Còn 7 ngày -> Không nằm trong danh sách cảnh báo
        FridgeItem safe = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Bí đỏ")
                .normalizedName("bi do")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .expiryDate(LocalDate.now().plusDays(7))
                .status(FridgeItemStatus.ACTIVE)
                .build();

        fridgeItemRepository.saveAll(List.of(urgent, safe));

        mockMvc.perform(get("/api/fridge/expiring-soon?days=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Giá đỗ"))
                .andExpect(jsonPath("$.data[0].isExpiringSoon").value(true));
    }

    @Test
    @DisplayName("GET /api/fridge/summary: Thống kê tổng quan kho tủ lạnh chính xác")
    void getFridgeSummary_correctStats() throws Exception {
        FridgeItem freshItem = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Cà rốt")
                .normalizedName("ca rot")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .freshnessStatus(FreshnessStatus.FRESH)
                .isVegetarian(true)
                .expiryDate(LocalDate.now().plusDays(10))
                .status(FridgeItemStatus.ACTIVE)
                .build();

        FridgeItem urgentItem = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Mồng tơi")
                .normalizedName("mong toi")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .freshnessStatus(FreshnessStatus.USABLE)
                .isVegetarian(true)
                .expiryDate(LocalDate.now().plusDays(1))
                .status(FridgeItemStatus.ACTIVE)
                .build();

        fridgeItemRepository.saveAll(List.of(freshItem, urgentItem));

        mockMvc.perform(get("/api/fridge/summary")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + primaryToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalActiveItems").value(2))
                .andExpect(jsonPath("$.data.freshCount").value(1))
                .andExpect(jsonPath("$.data.usableCount").value(1))
                .andExpect(jsonPath("$.data.expiringSoonCount").value(1))
                .andExpect(jsonPath("$.data.veganCount").value(2))
                .andExpect(jsonPath("$.data.urgentWarnings", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @DisplayName("Bảo mật dữ liệu: Người dùng B không thể xem hoặc sửa nguyên liệu của người dùng A")
    void ownershipProtection_cannotAccessOtherUserItem() throws Exception {
        FridgeItem itemOfPrimary = FridgeItem.builder()
                .userId(primaryUser.getUserId())
                .name("Món riêng của user A")
                .normalizedName("mon rieng cua user a")
                .firstScannedAt(LocalDateTime.now())
                .lastScannedAt(LocalDateTime.now())
                .expiryDate(LocalDate.now().plusDays(3))
                .status(FridgeItemStatus.ACTIVE)
                .build();
        itemOfPrimary = fridgeItemRepository.save(itemOfPrimary);

        // User B cố tình truy cập ID của User A
        mockMvc.perform(get("/api/fridge/items/" + itemOfPrimary.getItemId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondaryToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1801));

        // User B cố tình xóa ID của User A
        mockMvc.perform(delete("/api/fridge/items/" + itemOfPrimary.getItemId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + secondaryToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1801));
    }
}
