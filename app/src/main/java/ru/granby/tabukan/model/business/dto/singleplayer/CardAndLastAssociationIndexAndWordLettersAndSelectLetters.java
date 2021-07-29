package ru.granby.tabukan.model.business.dto.singleplayer;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import ru.granby.tabukan.model.data.database.relations.game.Card;

@AllArgsConstructor
@Data
public class CardAndLastAssociationIndexAndWordLettersAndSelectLetters {
    private Card card;
    private int lastAssociationIndex;
    private List<Character> wordLetters;
    private List<Character> selectLetters;
}
