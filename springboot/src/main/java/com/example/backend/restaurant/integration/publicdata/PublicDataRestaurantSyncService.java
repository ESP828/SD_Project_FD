package com.example.backend.restaurant.integration.publicdata;

import com.example.backend.restaurant.domain.entity.PublicRestaurant;
import com.example.backend.restaurant.integration.publicdata.dto.PublicDataStoreListResponse;
import com.example.backend.restaurant.repository.PublicRestaurantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 공공데이터 상가업소정보를 페이지 단위로 내려받아 {@link PublicRestaurant}로 적재한다.
 * 상가업소번호(bizesId) 기준으로 있으면 갱신, 없으면 신규 저장한다.
 */
@Service
public class PublicDataRestaurantSyncService {

    private static final Logger log = LoggerFactory.getLogger(PublicDataRestaurantSyncService.class);
    private static final int NUM_OF_ROWS = 1000;

    private final PublicDataRestaurantClient client;
    private final PublicRestaurantRepository repository;

    public PublicDataRestaurantSyncService(
            PublicDataRestaurantClient client,
            PublicRestaurantRepository repository
    ) {
        this.client = client;
        this.repository = repository;
    }

    public record SyncResult(int pagesProcessed, int rowsUpserted, long totalCount, boolean completed) {
    }

    /**
     * 지정한 시작 페이지부터 최대 maxPages 페이지까지 동기화한다.
     * 하루 API 호출(트래픽) 제한 때문에 한 번의 호출로 전국 데이터를 다 받지 못할 수 있어,
     * startPage/maxPages로 나눠서 여러 번에 걸쳐 이어서 호출하는 것을 전제로 한다.
     */
    public SyncResult sync(int startPage, int maxPages) {
        if (!client.isConfigured()) {
            throw new IllegalStateException("공공데이터 서비스키가 설정되지 않았습니다.");
        }
        int rowsUpserted = 0;
        int pagesProcessed = 0;
        long totalCount = 0;
        boolean completed = false;

        for (int page = startPage; page < startPage + maxPages; page++) {
            PublicDataStoreListResponse response = client.fetchFoodStores(page, NUM_OF_ROWS);
            if (response == null || response.header() == null) {
                log.warn("공공데이터 응답이 비어 있습니다. page={}", page);
                break;
            }
            if (response.header().isNoData()) {
                completed = true;
                break;
            }
            if (!response.header().isSuccess()) {
                log.warn(
                        "공공데이터 조회 실패. page={}, resultCode={}, resultMsg={}",
                        page, response.header().resultCode(), response.header().resultMsg()
                );
                break;
            }

            List<PublicDataStoreListResponse.Item> items = response.body().itemsOrEmpty();
            totalCount = response.body().totalCount();
            rowsUpserted += upsertAll(items, response.header().stdrYm());
            pagesProcessed++;

            if (items.size() < NUM_OF_ROWS
                    || (long) page * NUM_OF_ROWS >= response.body().totalCount()) {
                completed = true;
                break;
            }
        }

        return new SyncResult(pagesProcessed, rowsUpserted, totalCount, completed);
    }

    @Transactional
    protected int upsertAll(List<PublicDataStoreListResponse.Item> items, String dataYm) {
        int count = 0;
        for (PublicDataStoreListResponse.Item item : items) {
            if (item.bizesId() == null || item.bizesNm() == null) {
                continue;
            }
            PublicRestaurant restaurant = repository.findByExternalStoreId(item.bizesId())
                    .orElseGet(() -> new PublicRestaurant(item.bizesId(), item.bizesNm()));
            restaurant.update(
                    item.bizesNm(),
                    item.brchNm(),
                    item.indsLclsCd(),
                    item.indsLclsNm(),
                    item.indsMclsCd(),
                    item.indsMclsNm(),
                    item.indsSclsCd(),
                    item.indsSclsNm(),
                    item.ctprvnNm(),
                    item.signguNm(),
                    item.rdnmAdr(),
                    item.lnoAdr(),
                    toBigDecimal(item.lat()),
                    toBigDecimal(item.lon()),
                    dataYm
            );
            repository.save(restaurant);
            count++;
        }
        return count;
    }

    private BigDecimal toBigDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
