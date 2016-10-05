package camidion.chordhelper.mididevice;

import java.util.HashSet;
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
	 * このリストモデルの{@link Receiver}に接続された{@link Transmitter}を全て閉じます。
	 *
	 * @return 閉じた{@link Transmitter}の{@link MidiDeviceModel}の集合
	 */
	public Set<MidiDeviceModel> closePeerTransmitters() {
		List<Receiver> rxList = deviceModel.getMidiDevice().getReceivers();
		List<MidiDeviceModel> deviceModelList = deviceModel.getDeviceModelManager().getDeviceModelList();
		Set<MidiDeviceModel> peedDeviceModels = new HashSet<>();
		for( Receiver rx : rxList ) {
			for( MidiDeviceModel peedDeviceModel : deviceModelList ) {
				if( peedDeviceModel == deviceModel ) continue;
				TransmitterListModel txListModel = peedDeviceModel.getTransmitterListModel();
				if( txListModel == null || txListModel.closeTransmittersConnectedTo(rx).isEmpty() ) continue;
				peedDeviceModels.add(peedDeviceModel);
			}
		}
		return peedDeviceModels;
	}
}
