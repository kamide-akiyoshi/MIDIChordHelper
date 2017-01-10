package camidion.chordhelper.midieditor;

import javax.swing.JLabel;
import javax.swing.JPanel;

import camidion.chordhelper.music.Key;

/**
 * 調性選択フォーム
 */
public class KeySignatureSelecter extends JPanel {
	public KeySignatureComboBox getKeysigCombobox() { return keysigCombobox; }
	protected KeySignatureComboBox keysigCombobox = new KeySignatureComboBox();
	protected MinorCheckBox minorCheckbox = null;
	/**
	 * 調性選択フォームを構築します。
	 * 初期値としてメジャー・マイナーの区別がある調を指定した場合、
	 * メジャー・マイナーを選択できるminorチェックボックス付きで構築されます。
	 * @param defaultKey 調の初期値
	 */
	public KeySignatureSelecter(Key defaultKey) {
		add(new JLabel("Key:"));
		add(keysigCombobox);
		if(defaultKey.majorMinor() != Key.MajorMinor.MAJOR_OR_MINOR) {
			add(minorCheckbox = new MinorCheckBox());
		}
		setSelectedKey(defaultKey);
	}
	/**
	 * 調の選択を、引数で指定された通りに変更します。
	 * メジャー・マイナーの区別のない調が指定された場合、
	 * minorチェックボックスは変更されず、プルダウン選択のみが変更されます。
	 *
	 * @param key 選択する調
	 */
	public void setSelectedKey(Key key) {
		if( minorCheckbox != null ) minorCheckbox.setMajorMinor(key.majorMinor());
		keysigCombobox.setSelectedItem(key);
	}
	/**
	 * 選択されている調を返します。minorチェックボックスがある場合はメジャー・マイナーの区別つき、
	 * そうでない場合は区別なしの調を返します。
	 *
	 * @return 選択されている調
	 */
	public Key getSelectedKey() {
		Key key = (Key) keysigCombobox.getSelectedItem();
		if( minorCheckbox == null ) return key;
		return new Key(key.toCo5(), minorCheckbox.getMajorMinor());
	}
}
