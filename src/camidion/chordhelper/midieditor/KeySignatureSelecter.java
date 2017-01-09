package camidion.chordhelper.midieditor;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import camidion.chordhelper.music.Key;
import camidion.chordhelper.music.Note;

/**
 * 調性選択フォーム
 */
public class KeySignatureSelecter extends JPanel implements ActionListener {
	public JComboBox<Key> keysigCombobox = new JComboBox<Key>() {
		{
			for(int co5 = -Key.MAX_SHARPS_OR_FLATS ; co5 <= Key.MAX_SHARPS_OR_FLATS ; co5++)
				addItem(new Key(co5));
			setMaximumRowCount(getItemCount());
		}
	};
	JCheckBox minorCheckbox = null;

	/**
	 * 調性選択フォームを構築します。
	 * 初期値としてメジャー・マイナーの区別がある調を指定した場合、
	 * メジャー・マイナーを選択できるminorチェックボックス付きで構築されます。
	 * @param key 調の初期値
	 */
	public KeySignatureSelecter(Key key) {
		add(new JLabel("Key:"));
		add(keysigCombobox);
		if(key.majorMinor() != Key.MajorMinor.MAJOR_OR_MINOR) {
			add(minorCheckbox = new JCheckBox("minor"));
			minorCheckbox.addActionListener(this);
		}
		keysigCombobox.addActionListener(this);
		setSelectedKey(key);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		Key key = (Key)keysigCombobox.getSelectedItem();
		keysigCombobox.setToolTipText(
			"Key: " + key.toStringIn( Note.Language.NAME )
			+ " "  + key.toStringIn( Note.Language.IN_JAPANESE )
			+ " (" + key.signatureDescription() + ")"
		);
	}

	/**
	 * 調の選択を、引数で指定された通りに変更します。
	 * メジャー・マイナーの区別のない調が指定された場合、
	 * minorチェックボックスは変更されず、プルダウン選択のみが変更されます。
	 *
	 * @param key 選択する調
	 */
	public void setSelectedKey(Key key) {
		setMajorMinor(key.majorMinor());
		if( key.majorMinor() != Key.MajorMinor.MAJOR_OR_MINOR ) key = new Key(key.toCo5());
		keysigCombobox.setSelectedItem(key);
	}
	/**
	 * 選択されている調を返します。minorチェックボックスがある場合はメジャー・マイナーの区別つき、
	 * そうでない場合は区別なしの調を返します。
	 *
	 * @return 選択されている調
	 */
	public Key getSelectedKey() {
		Key key = (Key)keysigCombobox.getSelectedItem();
		return minorCheckbox == null ? key : new Key(key.toCo5(), getMajorMinor());
	}

	private void setMajorMinor(Key.MajorMinor majorMinor) {
		if( minorCheckbox == null || majorMinor == Key.MajorMinor.MAJOR_OR_MINOR ) return;
		minorCheckbox.setSelected(majorMinor == Key.MajorMinor.MINOR);
	}
	private Key.MajorMinor getMajorMinor() {
		return minorCheckbox == null ? Key.MajorMinor.MAJOR_OR_MINOR :
			minorCheckbox.isSelected() ? Key.MajorMinor.MINOR : Key.MajorMinor.MAJOR;
	}
}