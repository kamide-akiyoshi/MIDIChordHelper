package camidion.chordhelper.mididevice;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;

/**
 * {@link Receiver} のリストを表す {@link javax.swing.ListModel}
 */
public class ReceiverListModel extends AbstractTransceiverListModel<Receiver> {
	public ReceiverListModel(MidiDeviceModel deviceModel) { super(deviceModel); }
	protected List<Receiver> getTransceivers() {
		return deviceModel.getMidiDevice().getReceivers();
	}
	/**
	 * {@link Receiver}を最大1個開きます。
	 * @throws MidiUnavailableException リソースの制約のためにレシーバを使用できない場合にスローされる
	 */
	public void openSingleReceiver() throws MidiUnavailableException {
		MidiDevice device = deviceModel.getMidiDevice();
		if( device.getReceivers().isEmpty() ) device.getReceiver();
	}
	/**
	 * このリストモデルの{@link Receiver}に接続された{@link Transmitter}を全て閉じ、
	 * 接続相手だったMIDIデバイスモデルのユニークな集合を返します。
	 *
	 * @return 閉じた{@link Transmitter}の{@link MidiDeviceModel}の集合
	 */
	public Set<MidiDeviceModel> closeTransmitters() {
		Set<MidiDeviceModel> peerDeviceModelSet = new LinkedHashSet<>();
		List<Receiver> rxList = getTransceivers();
		if( ! rxList.isEmpty() ) {
			for( MidiDeviceModel peerDeviceModel : deviceModel.getDeviceTreeModel() ) {
				if( peerDeviceModel == deviceModel ) continue;
				TransmitterListModel txListModel = peerDeviceModel.getTransmitterListModel();
				if( txListModel == null ) continue;
				for( Receiver rx : rxList )
					if( ! txListModel.closeTransmittersFor(rx).isEmpty() )
						peerDeviceModelSet.add(peerDeviceModel);
			}
		}
		return peerDeviceModelSet;
	}
}
