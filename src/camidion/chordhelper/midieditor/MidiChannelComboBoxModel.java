package camidion.chordhelper.midieditor;

import javax.swing.ComboBoxModel;

/**
 * MIDIチャンネル選択コンボボックスモデル
 */
public interface MidiChannelComboBoxModel extends ComboBoxModel<Integer> {
	/**
	 * 選択中のMIDIチャンネルを返します。
	 * @return 選択中のMIDIチャンネル
	 */
	int getSelectedChannel();
	/**
	 * MIDIチャンネルの選択を変更します。
	 * @param channel 選択するMIDIチャンネル
	 */
	void setSelectedChannel(int channel);
}
