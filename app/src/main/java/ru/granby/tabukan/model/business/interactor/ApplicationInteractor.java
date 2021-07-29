package ru.granby.tabukan.model.business.interactor;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import lombok.Synchronized;
import ru.granby.tabukan.model.data.sharedpreferences.SharedPreferencesManager;

import static ru.granby.tabukan.model.business.interactor.keys.ApplicationKey.COIN_BALANCE;
import static ru.granby.tabukan.model.business.interactor.keys.ApplicationKey.COIN_BALANCE_DEFAULT_VALUE;

public class ApplicationInteractor extends BaseInteractor {
    @Synchronized
    public Single<Integer> getCoinBalance() {
        return get(COIN_BALANCE, SharedPreferencesManager.getInstance()
                .getInt(COIN_BALANCE, COIN_BALANCE_DEFAULT_VALUE));
    }

    @Synchronized
    public Completable setCoinBalance(int coinBalance) {
        return set(COIN_BALANCE, coinBalance, SharedPreferencesManager.getInstance()
                .putInt(COIN_BALANCE, coinBalance));
    }

    public Completable setDefaultCoinBalance() {
        return set(COIN_BALANCE, COIN_BALANCE_DEFAULT_VALUE, SharedPreferencesManager.getInstance()
                .putInt(COIN_BALANCE, COIN_BALANCE_DEFAULT_VALUE));
    }
}