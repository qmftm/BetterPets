package kr.qmftm.betterpets.storage;

import kr.qmftm.betterpets.domain.LifeStage;
import kr.qmftm.betterpets.domain.PetData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 저장소는 Bukkit 의 {@code YamlConfiguration} 만 쓰고 서버는 쓰지 않는다. 그래서 검증할 수 있고,
 * <b>데이터를 잃을 수 있는 유일한 자리</b>라 검증해야 한다.
 */
class YamlPetRepositoryTest {

    private static final Logger QUIET = Logger.getLogger("betterpets-test");
    private static final UUID OWNER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static YamlPetRepository repo(final Path dir) {
        return new YamlPetRepository(dir.toFile(), QUIET);
    }

    private static File fileOf(final Path dir) {
        return dir.resolve(OWNER + ".yml").toFile();
    }

    @Test
    @DisplayName("저장한 값이 그대로 다시 읽힌다")
    void roundTrips(@TempDir final Path dir) {
        final YamlPetRepository repository = repo(dir);
        final PetData saved = PetData.newBaby(OWNER, "dragon", 1_700_000_000_000L);
        saved.nickname("화룡이");
        saved.stage(LifeStage.ADULT);
        saved.canFly(true);
        repository.save(saved);

        final List<PetData> loaded = repository.loadOwner(OWNER);

        assertEquals(1, loaded.size());
        final PetData pet = loaded.getFirst();
        assertEquals(saved.petId(), pet.petId());
        assertEquals("dragon", pet.typeId());
        assertEquals("화룡이", pet.nickname());
        assertEquals(LifeStage.ADULT, pet.stage());
        assertTrue(pet.canFly());
    }

    @Test
    @DisplayName("소환 상태는 저장되지 않는다 — 런타임 사실이라 기동 직후엔 언제나 false 다")
    void activeIsNotPersisted(@TempDir final Path dir) {
        final YamlPetRepository repository = repo(dir);
        final PetData saved = PetData.newBaby(OWNER, "wolf", 1L);
        saved.active(true);
        repository.save(saved);

        assertFalse(repository.loadOwner(OWNER).getFirst().active());
    }

    @Test
    @DisplayName("여러 마리를 묶어 저장해도 파일 하나에 다 들어간다")
    void savesManyIntoOneFile(@TempDir final Path dir) {
        final YamlPetRepository repository = repo(dir);
        repository.saveAll(List.of(
            PetData.newBaby(OWNER, "wolf", 1L),
            PetData.newBaby(OWNER, "dragon", 2L),
            PetData.newBaby(OWNER, "pig", 3L)));

        assertEquals(3, repository.loadOwner(OWNER).size());
    }

    @Test
    @DisplayName("한 마리가 깨져도 나머지는 살린다")
    void oneBrokenEntryDoesNotLoseTheRest(@TempDir final Path dir) throws Exception {
        // petId 자리에 UUID 가 아닌 값이 들어간 경우다. 손으로 파일을 고치다 흔히 난다.
        Files.writeString(fileOf(dir).toPath(), """
            pets:
              그건-uuid-가-아니다:
                type: wolf
              66666666-7777-8888-9999-000000000000:
                type: dragon
                stage: ADULT
            """, StandardCharsets.UTF_8);

        final List<PetData> loaded = repo(dir).loadOwner(OWNER);

        assertEquals(1, loaded.size(), "읽을 수 있는 한 마리는 살아야 한다");
        assertEquals("dragon", loaded.getFirst().typeId());
    }

    @Test
    @DisplayName("pets 섹션이 없는데 내용은 있는 파일은 옆으로 치우고 원본을 남긴다")
    void unreadableFileIsQuarantined(@TempDir final Path dir) throws Exception {
        // Bukkit 은 YAML 파싱에 실패해도 예외를 던지지 않고 빈 설정을 준다. 그래서
        // "펫이 없는 플레이어"와 "파일이 깨진 플레이어"가 똑같이 보이고, 그대로 두면
        // 다음 저장이 원본을 덮어써 복구할 길이 사라진다.
        //
        // 진짜 깨진 YAML 로는 여기서 재현할 수 없다 — Bukkit 이 경고를 남기려고
        // Bukkit.getLogger() 를 부르는데 테스트에는 서버가 없다. 우리 코드가 보는
        // 결과(내용은 있는데 pets 섹션이 없다)는 같으므로 그 상태로 확인한다.
        final String foreign = "이건: 우리파일이아니다\n다른키: 42\n";
        Files.writeString(fileOf(dir).toPath(), foreign, StandardCharsets.UTF_8);

        assertTrue(repo(dir).loadOwner(OWNER).isEmpty());

        assertFalse(fileOf(dir).exists(), "원본 자리에서 치워져야 한다");
        final File[] rescued = dir.toFile().listFiles((d, name) -> name.contains(".broken-"));
        assertEquals(1, rescued.length, "복구용 파일이 남아야 한다");
        assertEquals(foreign, Files.readString(rescued[0].toPath(), StandardCharsets.UTF_8),
            "내용이 그대로 보존돼야 한다");
    }

    @Test
    @DisplayName("펫을 전부 놓아준 파일은 치우지 않는다 — 이 변경의 오탐 위험이 여기 있다")
    void releasingEveryPetIsNotSuspicious(@TempDir final Path dir) {
        // 정상적인 빈 상태를 "깨졌다"고 판정하면, 펫을 다 놓아준 플레이어가 접속할
        // 때마다 파일이 옆으로 치워지고 쓰레기가 쌓인다.
        final YamlPetRepository repository = repo(dir);
        final PetData only = PetData.newBaby(OWNER, "wolf", 1L);
        repository.save(only);
        repository.delete(OWNER, only.petId());

        assertTrue(repository.loadOwner(OWNER).isEmpty());

        assertTrue(fileOf(dir).exists(), "정상적으로 비어 있는 파일은 그대로 둔다");
        assertEquals(0, dir.toFile().listFiles((d, name) -> name.contains(".broken-")).length);
    }

    @Test
    @DisplayName("정말 비어 있는 파일은 치우지 않는다 — 접속마다 쓰레기가 쌓이면 안 된다")
    void emptyFileIsLeftAlone(@TempDir final Path dir) throws Exception {
        Files.writeString(fileOf(dir).toPath(), "", StandardCharsets.UTF_8);

        assertTrue(repo(dir).loadOwner(OWNER).isEmpty());

        assertTrue(fileOf(dir).exists());
        assertEquals(0, dir.toFile().listFiles((d, name) -> name.contains(".broken-")).length);
    }

    @Test
    @DisplayName("파일이 아예 없으면 조용히 빈 목록")
    void missingFileIsNotAnError(@TempDir final Path dir) {
        assertTrue(repo(dir).loadOwner(UUID.randomUUID()).isEmpty());
    }

    @Test
    @DisplayName("지우면 그 펫만 빠지고 나머지는 남는다")
    void deleteRemovesOnlyOne(@TempDir final Path dir) {
        final YamlPetRepository repository = repo(dir);
        final PetData keep = PetData.newBaby(OWNER, "wolf", 1L);
        final PetData drop = PetData.newBaby(OWNER, "pig", 2L);
        repository.saveAll(List.of(keep, drop));

        repository.delete(OWNER, drop.petId());

        final List<PetData> left = repository.loadOwner(OWNER);
        assertEquals(1, left.size());
        assertEquals(keep.petId(), left.getFirst().petId());
    }
}
