package com.lankapay.city.store;

import com.lankapay.city.domain.CityMerchant;
import com.lankapay.city.domain.CityOffer;

import java.util.List;
import java.util.Optional;

public interface MerchantStore {
    List<CityMerchant> findAllMerchants();
    Optional<CityMerchant> findMerchantById(String id);
    CityMerchant saveMerchant(CityMerchant merchant);
    boolean deleteMerchant(String id);

    List<CityOffer> findAllOffers();
    List<CityOffer> findOffersByMerchantId(String merchantId);
    Optional<CityOffer> findOfferById(String id);
    CityOffer saveOffer(CityOffer offer);
    boolean deleteOffer(String id);
}
