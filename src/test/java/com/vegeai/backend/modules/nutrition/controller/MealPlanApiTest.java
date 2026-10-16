package com.vegeai.backend.modules.nutrition.controller;
import com.fasterxml.jackson.databind.*;
import com.vegeai.backend.support.GeminiIntegrationTestSupport;
import com.vegeai.backend.modules.auth.service.*;
import com.vegeai.backend.modules.user.entity.*;
import com.vegeai.backend.modules.user.repository.UserRepository;
import com.vegeai.backend.modules.recipe.entity.*;
import com.vegeai.backend.modules.recipe.entity.Tag;
import com.vegeai.backend.modules.recipe.repository.RecipeRepository;
import com.vegeai.backend.modules.recipe.repository.TagRepository;
import com.vegeai.backend.modules.community.entity.*;
import com.vegeai.backend.modules.community.repository.VideoRepository;
import com.vegeai.backend.modules.mealplan.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.net.URI;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class MealPlanApiTest extends GeminiIntegrationTestSupport {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired UserRepository users;
    @Autowired RecipeRepository recipes; @Autowired VideoRepository videos; @Autowired JwtService jwt;
    @Autowired RecipeDietPolicy policy; @MockBean TokenBlacklistService blacklist;
    @Autowired TagRepository tags;
    User member, other, admin; String token, otherToken, adminToken;
    @BeforeEach void setup() {
        member=user(UserRole.MEMBER); other=user(UserRole.MEMBER); admin=user(UserRole.ADMIN);
        token=jwt.generateAccessToken(member); otherToken=jwt.generateAccessToken(other); adminToken=jwt.generateAccessToken(admin);
        for(int i=0;i<3;i++) recipe("Vegetable dish " + i,"Rau cải",300+i*100);
    }
    User user(UserRole role) {
        String id=UUID.randomUUID().toString();
        return users.saveAndFlush(User.builder().username("plan-"+id).email(id+"@example.test").fullName("Plan fixture")
                .role(role).status(AccountStatus.ACTIVE).passwordHash("test-only").build());
    }
    Recipe recipe(String title,String ingredient,int calories) {
        Recipe r=Recipe.builder().title(title).slug(UUID.randomUUID().toString()).servings(2).calories(calories)
                .proteinGrams(20.0).carbsGrams(40.0).fatGrams(12.0).fiberGrams(5.0).cookTimeMinutes(10)
                .instructions(new ArrayList<>(List.of("Nấu rau với nước sạch."))).build();
        r.addIngredient(RecipeIngredient.builder().name(ingredient).normalizedName(ingredient).amount(200.0).unit("g").build());
        return recipes.saveAndFlush(r);
    }
    JsonNode call(String method,String path,String auth,Object body,int code)throws Exception {
        var req=request(HttpMethod.valueOf(method), URI.create(path));
        if(auth!=null)req.header(HttpHeaders.AUTHORIZATION,"Bearer "+auth);
        if(body!=null)req.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var res=mvc.perform(req).andExpect(status().is(code)).andReturn();
        return mapper.readTree(res.getResponse().getContentAsString()).path("data");
    }
    Map<String,Object> profileBody() {
        return new HashMap<>(Map.of("gender","FEMALE","dateOfBirth","2000-01-15","heightCm",160,"weightKg",55,
                "activityLevel","MODERATE","goal","MAINTAIN","phoneNumber","0912345678","dietaryPreferences","VEGAN","allergies","SOY"));
    }
    JsonNode profile()throws Exception {return call("PUT","/api/nutrition/profile",token,profileBody(),200);}
    Map<String,Object> generateBody(String period) {return Map.of("period",period,"startDate",LocalDate.now().toString(),"mealsPerDay",3);}
    JsonNode generate(String period)throws Exception {return call("POST","/api/meal-plans/generate",token,generateBody(period),200);}

    @Test void nutritionContactDietAndRestrictionsAreAtomicAndShared()throws Exception {
        profile(); JsonNode account=call("GET","/api/users/me",token,null,200);
        assertThat(account.path("phoneNumber").asText()).isEqualTo("0912345678");
        assertThat(account.path("dietaryPreferences").asText()).isEqualTo("VEGAN");
        assertThat(call("GET","/api/nutrition/profile",token,null,200).path("allergies").asText()).isEqualTo("SOY");
        var invalid=profileBody();invalid.put("phoneNumber","abc");invalid.put("weightKg",90);
        call("PUT","/api/nutrition/profile",token,invalid,400);
        assertThat(call("GET","/api/nutrition/profile",token,null,200).path("weightKg").asDouble()).isEqualTo(55);
        var omitted=profileBody();omitted.remove("phoneNumber");omitted.remove("dietaryPreferences");omitted.remove("allergies");
        assertThat(call("PUT","/api/nutrition/profile",token,omitted,200).path("phoneNumber").asText()).isEqualTo("0912345678");
    }
    @Test void weekPreviewSwapSaveAndOwnerListingUseServerCalculatedNutrition()throws Exception {
        profile(); var soy=recipe("Soy forbidden","Đậu phụ",500);
        JsonNode draft=generate("WEEK");String id=draft.path("id").asText();
        assertThat(draft.path("status").asText()).isEqualTo("DRAFT");
        assertThat(draft.path("plan").path("days").size()).isEqualTo(7);
        assertThat(call("GET","/api/meal-plans",token,null,200).path("totalElements").asInt()).isZero();
        for(JsonNode day:draft.path("plan").path("days")) {
            double sum=0;
            for(JsonNode meal:day.path("meals")) {
                UUID recipeId=UUID.fromString(meal.path("recipeId").asText());assertThat(recipeId).isNotEqualTo(soy.getRecipeId());
                Recipe r=recipes.findById(recipeId).orElseThrow();assertThat(policy.allowed(r,member)).isTrue();
                assertThat(meal.path("nutrition").path("calories").asDouble()).isEqualTo(r.getCalories()*meal.path("portions").asDouble());
                sum+=meal.path("nutrition").path("calories").asDouble();
            }
            assertThat(day.path("totals").path("calories").asDouble()).isEqualTo(sum);
        }
        assertThat(lastRequest.toString()).doesNotContain("0912345678",member.getEmail(),member.getFullName());
        assertThat(lastRequest.toString()).contains("proteinTargetG","carbsTargetG","fatTargetG","SOY");
        JsonNode options=call("GET","/api/meal-plans/"+id+"/options",token,null,200);
        String replacement=options.get(0).path("recipeId").asText();
        JsonNode swapped=call("PUT","/api/meal-plans/"+id+"/days/0/meals/0",token,Map.of("recipeId",replacement),200);
        assertThat(swapped.path("plan").path("days").get(0).path("meals").get(0).path("recipeId").asText()).isEqualTo(replacement);
        call("PUT","/api/meal-plans/"+id+"/days/0/meals/0",token,Map.of("recipeId",soy.getRecipeId()),400);
        assertThat(call("POST","/api/meal-plans/"+id+"/save",token,Map.of(),200).path("status").asText()).isEqualTo("SAVED");
        assertThat(call("GET","/api/meal-plans",token,null,200).path("totalElements").asInt()).isEqualTo(1);
        call("POST","/api/meal-plans/"+id+"/save",token,Map.of(),200);
        call("PUT","/api/meal-plans/"+id+"/days/0/meals/0",token,Map.of("recipeId",replacement),409);
        for(String suffix:List.of("","/options"))call("GET","/api/meal-plans/"+id+suffix,otherToken,null,404);
        call("POST","/api/meal-plans/"+id+"/save",otherToken,Map.of(),404);
        assertThat(call("GET","/api/meal-plans",otherToken,null,200).path("totalElements").asInt()).isZero();
    }
    @Test void monthLengthIsCalendarBasedAndProfileChangesInvalidateDraft()throws Exception {
        profile();JsonNode draft=generate("MONTH");String id=draft.path("id").asText();
        assertThat(draft.path("plan").path("days").size()).isEqualTo((int)java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(),LocalDate.now().plusMonths(1)));
        var body=profileBody();body.put("weightKg",60);call("PUT","/api/nutrition/profile",token,body,200);
        call("POST","/api/meal-plans/"+id+"/save",token,Map.of(),409);
    }
    @Test void missingProfilesGuestAdminInvalidRangesAndHallucinatedIdsAreRejected()throws Exception {
        call("POST","/api/meal-plans/generate",null,generateBody("WEEK"),401);
        call("GET","/api/meal-plans",adminToken,null,403);
        call("POST","/api/meal-plans/generate",token,generateBody("WEEK"),403);
        profile();var body=new HashMap<>(generateBody("WEEK"));body.put("mealsPerDay",7);
        call("POST","/api/meal-plans/generate",token,body,400);
        body.put("mealsPerDay",3);body.put("startDate",LocalDate.now().minusDays(10).toString());
        call("POST","/api/meal-plans/generate",token,body,400);
        var p=profileBody();p.put("allergies","fixture:rank-invalid");call("PUT","/api/nutrition/profile",token,p,200);
        call("POST","/api/meal-plans/generate",token,generateBody("WEEK"),502);
    }

    @Test void dietaryRulesCoverNamedAvoidancesCodesAndNonVegetarianIngredients()throws Exception {
        profile();
        for(String ingredient:List.of("Đậu phụ", "Nước tương", "Sữa bò", "Trứng gà", "Thịt heo", "Tôm"))
            assertThat(policy.allowed(recipe("Excluded "+ingredient,ingredient,400),member)).as(ingredient).isFalse();
        member.setAllergies("Mè, hành tây");users.saveAndFlush(member);
        assertThat(policy.allowed(recipe("Sesame","Dầu mè",400),member)).isFalse();
        assertThat(policy.allowed(recipe("Onion","Hành tây",400),member)).isFalse();
        assertThat(policy.allowed(recipe("Carrot","Cà rốt",400),member)).isTrue();
    }
    @Test void missingRecipeDataIsExcludedAndRecipeChangesAreCheckedBeforeSaving()throws Exception {
        profile();Recipe incomplete=recipe("Incomplete","Cà rốt",400);incomplete.setFatGrams(null);recipes.saveAndFlush(incomplete);
        JsonNode plan=generate("WEEK");String id=plan.path("id").asText();
        for(JsonNode day:plan.path("plan").path("days"))for(JsonNode meal:day.path("meals"))assertThat(meal.path("recipeId").asText()).isNotEqualTo(incomplete.getRecipeId().toString());
        String chosen=plan.path("plan").path("days").get(0).path("meals").get(0).path("recipeId").asText();
        Recipe r=recipes.findById(UUID.fromString(chosen)).orElseThrow();r.setStatus(RecipeStatus.ARCHIVED);recipes.saveAndFlush(r);
        call("POST","/api/meal-plans/"+id+"/save",token,Map.of(),409);
    }
    @Test void condimentsDessertsAndSideDishesAreNotIndependentMeals()throws Exception {
        profile(); Set<String> excluded=new HashSet<>();
        for(String code:List.of("CONDIMENT","DESSERT","SIDE_DISH")) {
            Tag tag=tags.saveAndFlush(Tag.builder().code(code).name(code).group(TagGroup.DISH_CATEGORY).build());
            Recipe r=recipe(code,"Cà rốt",400); r.getTags().add(tag); recipes.saveAndFlush(r);
            excluded.add(r.getRecipeId().toString());
        }
        JsonNode plan=generate("WEEK"); String id=plan.path("id").asText();
        for(JsonNode day:plan.path("plan").path("days"))for(JsonNode meal:day.path("meals"))
            assertThat(excluded).doesNotContain(meal.path("recipeId").asText());
        for(JsonNode option:call("GET","/api/meal-plans/"+id+"/options",token,null,200))
            assertThat(excluded).doesNotContain(option.path("recipeId").asText());
        for(String forbidden:excluded)
            call("PUT","/api/meal-plans/"+id+"/days/0/meals/0",token,Map.of("recipeId",forbidden),400);
        String selected=plan.path("plan").path("days").get(0).path("meals").get(0).path("recipeId").asText();
        Recipe changed=recipes.findById(UUID.fromString(selected)).orElseThrow();
        changed.getTags().add(tags.findByCodeIgnoreCase("DESSERT").orElseThrow());recipes.saveAndFlush(changed);
        call("POST","/api/meal-plans/"+id+"/save",token,Map.of(),409);
    }
    @Test void linkedPublishedVideosAreIncludedButDraftVideosAreNot()throws Exception {
        profile();
        var publicVideo=new CommunityVideo();publicVideo.setTitle("Public cooking video");publicVideo.setCreatedBy(admin);publicVideo.setStatus(VideoStatus.PUBLISHED);publicVideo.setVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");videos.saveAndFlush(publicVideo);
        var privateVideo=new CommunityVideo();privateVideo.setTitle("Private draft video");privateVideo.setCreatedBy(admin);privateVideo.setStatus(VideoStatus.DRAFT);privateVideo.setVideoUrl(publicVideo.getVideoUrl());videos.saveAndFlush(privateVideo);
        for(Recipe r:recipes.findAll()){r.getVideos().add(publicVideo);r.getVideos().add(privateVideo);recipes.save(r);}recipes.flush();
        JsonNode plan=generate("WEEK");
        for(JsonNode day:plan.path("plan").path("days"))for(JsonNode meal:day.path("meals")) {
            assertThat(meal.path("recipe").path("videos").size()).isEqualTo(1);
            assertThat(meal.path("recipe").path("videos").get(0).path("id").asText()).isEqualTo(publicVideo.getId().toString());
        }
    }
}
