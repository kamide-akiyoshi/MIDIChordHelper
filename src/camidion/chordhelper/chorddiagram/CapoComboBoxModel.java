package camidion.chordhelper.chorddiagram;

import javax.swing.DefaultComboBoxModel;

/**
 * カポ選択コンボボックスモデル（選択範囲：1～11）
 */
public class CapoComboBoxModel extends DefaultComboBoxModel<Integer> {
	{ for( int i=1; i<=11; i++ ) addElement(i); }
}
