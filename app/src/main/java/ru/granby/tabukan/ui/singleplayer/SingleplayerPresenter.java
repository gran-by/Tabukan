package ru.granby.tabukan.ui.singleplayer;

import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import ru.granby.tabukan.exception.AlreadyShownAllAssociationsException;
import ru.granby.tabukan.exception.IncorrectCardIndexException;
import ru.granby.tabukan.exception.NotEnoughBalanceException;
import ru.granby.tabukan.exception.WordLettersAreFullException;
import ru.granby.tabukan.localization.Localization;
import ru.granby.tabukan.model.business.helpers.CardIndexHelper;
import ru.granby.tabukan.model.business.helpers.CoinBalanceHelper;
import ru.granby.tabukan.model.data.database.relations.game.Card;
import ru.granby.tabukan.model.business.dto.singleplayer.CardAndLastAssociationIndexAndWordLettersAndSelectLetters;
import ru.granby.tabukan.model.business.dto.singleplayer.CardAndWordLettersAndSelectLetters;
import ru.granby.tabukan.model.business.dto.singleplayer.CoinBalanceAndCardIndex;
import ru.granby.tabukan.ui.base.BasePresenter;

import static ru.granby.tabukan.ui.singleplayer.SingleplayerContract.BLANK_LETTER;

public class SingleplayerPresenter extends BasePresenter<SingleplayerContract.View, SingleplayerContract.Interactor> implements SingleplayerContract.Presenter {

    private static final String TAG = "~SingleplayerPresenter";

    @Override
    public void bind(SingleplayerContract.View view, SingleplayerContract.Interactor interactor) {
        super.bind(view, interactor);

        interactor.addDisposable(
                interactor.isSingleplayerFirstLaunch()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                        .flatMapCompletable((result) -> result ? interactor.setSingleplayerFirstLaunch(false) : Completable.complete())
                    .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            () -> {},
                            (throwable) -> Log.e(TAG, "can't isSingleplayerFirstLaunch: ", throwable)
                        )
        );
    }

    @Override
    public void initAds() {
        interactor.addDisposable(
                interactor.getAdRequest()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            view::showAdBanner,
                            (throwable -> Log.e(TAG, "can't getAdRequest: ", throwable))
                        )
        );
    }

    @Override
    public void onBackClicked() {
        view.finishView();
    }

    @Override
    public void showCoinBalance() {
        interactor.addDisposable(
                interactor.getCoinBalance()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            view::showCoinBalance,
                            (throwable) -> Log.e(TAG, "can't getCoinBalance: ", throwable)
                        )
        );
    }

    @Override
    public void showCurrentCard() {
        interactor.addDisposable(
                interactor.getCurrentCardIndex()
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                            this::showCard,
                            (throwable) -> Log.e(TAG, "can't getCurrentCardIndex: ", throwable)
                        )
        );
    }

    @Override
    public void onWordLetterClicked(int wordLetterIndex, String wordLetterText) {
        if(wordLetterText.equals(BLANK_LETTER.toString())) {
            return;
        }

        interactor.addDisposable(
                Single.zip(
                        interactor.getCurrentCard(),
                        interactor.getCurrentWordLetters(),
                        interactor.getCurrentSelectLetters(),
                        CardAndWordLettersAndSelectLetters::new
                )
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .map(dto -> {
                    dto.getWordLetters().set(wordLetterIndex, BLANK_LETTER);
                    dto.getSelectLetters().set(dto.getSelectLetters().indexOf(BLANK_LETTER),
                            wordLetterText.charAt(0));
                    return dto;
                })
                .flatMap(dto ->
                        Completable.mergeArray(
                                interactor.setCurrentWordLetters(dto.getWordLetters()),
                                interactor.setCurrentSelectLetters(dto.getSelectLetters())
                        ).andThen(Single.just(dto))
                )
                .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((dto) -> {
                        view.showWordLetters(dto.getWordLetters());
                        view.showSelectLetters(dto.getSelectLetters());
                    }, (throwable -> Log.e(TAG, "can't handle onWordLetterClicked", throwable)))
        );
    }

    @Override
    public void onSelectLetterClicked(int selectLetterIndex, String selectLetterText) {
        interactor.addDisposable(
                Single.zip(
                        interactor.getCurrentCard(),
                        interactor.getCurrentWordLetters(),
                        interactor.getCurrentSelectLetters(),
                        CardAndWordLettersAndSelectLetters::new)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                    .map(dto -> {
                        int nextWordLetterToInsertIndex = dto.getWordLetters().indexOf(BLANK_LETTER);
                        if(dto.getWordLetters().isEmpty()) {
                            throw new IllegalStateException("wordLetters are empty");
                        }
                        else if(nextWordLetterToInsertIndex == -1) {
                            throw new WordLettersAreFullException("Can't find BLANK_LETTER in wordLetters");
                        } else {
                            dto.getWordLetters().set(nextWordLetterToInsertIndex, selectLetterText.charAt(0));
                            dto.getSelectLetters().set(selectLetterIndex, BLANK_LETTER);
                            return dto;
                        }
                    })
                .flatMap(dto ->
                    Completable.mergeArray(
                            interactor.setCurrentWordLetters(dto.getWordLetters()),
                            interactor.setCurrentSelectLetters(dto.getSelectLetters())
                    ).andThen(Single.just(dto))
                )
                .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((dto) -> {
                            view.showWordLetters(dto.getWordLetters());
                            view.showSelectLetters(dto.getSelectLetters());

                            if(dto.getWordLetters().contains(BLANK_LETTER)) {
                               return;
                            }

                            if(areWordLettersCorrect(dto.getWordLetters(), dto.getCard())) {
                                view.showAllAssociations();
                                //TODO: replace maxCoins and receivedCoins with normal (calculated, constant) values
                                view.showLevelPassedDialog(5, 1);
                            } else {
                                view.showWordIsIncorrect();
                            }
                        }, (throwable) -> {
                            if (throwable instanceof WordLettersAreFullException)
                                Log.d(TAG, "didn't finish onSelectLetterClicked: wordLetters are full");
                            else Log.e(TAG, "can't handle onSelectLetterClicked", throwable);
                        }
                    )
        );
    }

    @Override
    public void onRemoveNeedlessSelectLettersClicked() {
        interactor.addDisposable(
                interactor.getCurrentCard()
                    .subscribeOn(Schedulers.io())
                    .observeOn(Schedulers.io())
                        .flatMap(card -> {
                            //TODO: check coin balance

                            String cardHeader = Localization.getInstance().getText(
                                    card.getHeader().getLocalizedText());
                            ArrayList<Character> selectLetters = new ArrayList<>();
                            for (int i = 0; i < cardHeader.length(); i++) {
                                selectLetters.add(cardHeader.charAt(i));
                            }
                            Collections.shuffle(selectLetters);

                            List<Character> wordLetters = new ArrayList<>();
                            createBlankWordLettersList(card, wordLetters);

                            return Completable.mergeArray(
                                    interactor.setCurrentSelectLetters(selectLetters),
                                    interactor.setCurrentWordLetters(wordLetters)
                            ).andThen(Single.just(selectLetters));
                        })
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((selectLetters) -> {
                            view.showBlankWordLetters();
                            view.showSelectLetters(selectLetters);
                        }, (throwable -> Log.e(TAG, "can't onRemoveNeedlessSelectLettersClicked", throwable))
                    )
        );
    }

    @Override
    public void onNextAssociationClicked() {
        interactor.addDisposable(
                Single.zip(
                        interactor.getCurrentCard(),
                        interactor.getLastAssociationIndex(),
                        (card, lastShownAssociationIndex) -> {
                            int nextAssociationIndexToShow = lastShownAssociationIndex + 1;
                            if(nextAssociationIndexToShow >= card.getAssociations().size()) {
                                throw new AlreadyShownAllAssociationsException("nextAssociationIndex >= associationsCount");
                            }
                            return nextAssociationIndexToShow;
                        }
                )
                .flatMap(nextAssociationIndexToShow ->
                        interactor.setLastAssociationIndex(nextAssociationIndexToShow)
                            .andThen(Single.just(nextAssociationIndexToShow))
                )
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(
                            view::showAssociation,
                        (throwable) -> {
                            if(throwable instanceof AlreadyShownAllAssociationsException)
                                Log.d(TAG, "didn't finish onNextAssociationClicked: already shown all associations");
                            else
                                Log.e(TAG, "can't show next association", throwable);
                        })
        );
    }

    @Override
    public void onSkipCardClicked() {
        interactor.addDisposable(
                Single.zip(
                        interactor.getCoinBalance(),
                        interactor.getCardsCount(),
                        interactor.getCurrentCardIndex(),
                        (coinBalance, cardsCount, cardIndex) -> {
                            coinBalance = CoinBalanceHelper.withdraw(coinBalance, SingleplayerContract.SKIP_CARD_PRICE);
                            cardIndex = CardIndexHelper.changeIndexTo(cardIndex + 1, cardsCount);
                            return new CoinBalanceAndCardIndex(coinBalance, cardIndex);
                        }
                ).subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                    .flatMap(dto ->
                            Completable.mergeArray(
                                    interactor.setCoinBalance(dto.getCoinBalance()),
                                    interactor.setCurrentCardIndex(dto.getCardIndex()),
                                    interactor.setLastAssociationIndex(0),
                                    interactor.setCurrentWordLetters(new ArrayList<>()),
                                    interactor.setCurrentSelectLetters(new ArrayList<>())
                            ).andThen(Single.just(dto))
                    )
                .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((coinBalanceAndCardIndex) -> {
                        showCard(coinBalanceAndCardIndex.getCardIndex());
                        view.showCoinBalance(coinBalanceAndCardIndex.getCoinBalance());
                    }, (throwable) -> {
                        if(throwable instanceof NotEnoughBalanceException) {
                            Log.i(TAG, "onSkipCardClicked: not enough balance");
                            view.showNotEnoughBalance();
                        } else if(throwable instanceof IncorrectCardIndexException) {
                            Log.i(TAG, "onSkipCardClicked: incorrect card index");
                            view.showNoMoreLevelsDialog();
                        } else {
                            Log.e(TAG, "can't skip card: ", throwable);
                        }
                    })
        );
    }

    private void showCard(int cardIndex) {
        interactor.addDisposable(
                Single.zip(
                        interactor.getCardByIndex(cardIndex),
                        interactor.getLastAssociationIndex(),
                        interactor.getCurrentWordLetters(),
                        interactor.getCurrentSelectLetters(),
                        CardAndLastAssociationIndexAndWordLettersAndSelectLetters::new
                )
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                    .flatMap(dto -> {
                        if(dto.getWordLetters().isEmpty()) {
                            createBlankWordLettersList(dto.getCard(), dto.getWordLetters());
                            return interactor.setCurrentWordLetters(dto.getWordLetters())
                                    .andThen(Single.just(dto));
                        }

                        return Single.just(dto);
                    })
                    .flatMap(dto -> {
                        if(dto.getSelectLetters().isEmpty()) {
                            createSelectLettersList(dto.getCard(), dto.getSelectLetters());
                            return interactor.setCurrentSelectLetters(dto.getSelectLetters())
                                    .andThen(Single.just(dto));
                        }

                        return Single.just(dto);
                    })
                    .flatMap((dto) ->
                        interactor.setCurrentCard()
                            .andThen(Single.just(dto))
                    )
                .observeOn(AndroidSchedulers.mainThread())
                    .subscribe((dto -> {
                            view.setUpAssociations(dto.getCard().getAssociations());
                            view.showAssociationsUpTo(dto.getLastAssociationIndex());
                            view.showCurrentLevel(cardIndex + 1);
                            view.showWordLetters(dto.getWordLetters());
                            view.showSelectLetters(dto.getSelectLetters());
                    }), (throwable) -> Log.e(TAG, "can't showCard: ", throwable)
                )
        );
    }

    private void createBlankWordLettersList(Card card, List<Character> wordLetters) {
        for (int i = 0; i < Localization.getInstance().getText(card.getHeader().getLocalizedText()).length(); i++) {
            wordLetters.add(BLANK_LETTER);
        }
    }

    private void createSelectLettersList(Card card, List<Character> selectLetters) {
        String cardHeader = Localization.getInstance().getText(card.getHeader().getLocalizedText());

        for (int i = 0; i < cardHeader.length(); i++) {
            selectLetters.add(cardHeader.charAt(i));
        }

        char[] alphabetLetters = Localization.getInstance().getAlphabet();
        for (int i = 0; i < SingleplayerContract.SELECT_LETTERS_COUNT - cardHeader.length(); i++) {
            selectLetters.add(alphabetLetters[new Random().nextInt(alphabetLetters.length)]);
        }

        Collections.shuffle(selectLetters);
    }

    private boolean areWordLettersCorrect(List<Character> wordLetters, Card card) {
        String cardHeader = Localization.getInstance()
                .getText(card.getHeader().getLocalizedText())
                .toLowerCase();

        for (int i = 0; i < wordLetters.size(); i++) {
            if(!wordLetters.get(i).equals(cardHeader.charAt(i))) {
                return false;
            }
        }

        return true;
    }
}