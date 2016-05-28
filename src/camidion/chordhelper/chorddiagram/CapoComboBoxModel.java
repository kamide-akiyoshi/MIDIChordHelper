package camidion.chordhelper.chorddiagram;

import javax.swing.ComboBoxModel;
import javax.swing.event.ListDataListener;

/**
 * カポ選択コンボボックスモデル（選択範囲：1～11）
 */
public class CapoComboBoxModel implements ComboBoxModel<Integer> {
	private Integer selectedValue = Integer.valueOf(1);
	@Override
	public int getSize() { return 11; }
	@Override
	public Integer getElementAt(int index) { return Integer.valueOf(index + 1); }
	@Override
	public void addListDataListener(ListDataListener l) { }
	@Override
	public void removeListDataListener(ListDataListener l) { }
	@Override
	public void setSelectedItem(Object item) { selectedValue = (Integer)item; }
	@Override
	public Object getSelectedItem() { return selectedValue; }
}
