package camidion.chordhelper.midieditor;

import javax.swing.ComboBoxModel;

/**
 * MIDIチャンネル選択コンボボックスモデル
 */
public interface MidiChannelComboBoxModel extends ComboBoxModel<Integer> {
	int getSelectedChannel();
	void setSelectedChannel(int channel);
}