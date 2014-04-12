package camidion.chordhelper.midieditor;

import javax.swing.DefaultComboBoxModel;

import camidion.chordhelper.music.MIDISpec;

/**
 * MIDIチャンネル選択コンボボックスモデルのデフォルト実装
 */
public class DefaultMidiChannelComboBoxModel extends DefaultComboBoxModel<Integer>
	implements MidiChannelComboBoxModel
{
	public DefaultMidiChannelComboBoxModel() {
		for(int ch = 1; ch <= MIDISpec.MAX_CHANNELS ; ch++) addElement(ch);
	}
	public int getSelectedChannel() {
		return getIndexOf(getSelectedItem());
	}
	public void setSelectedChannel(int channel) {
		setSelectedItem(getElementAt(channel));
	}
}