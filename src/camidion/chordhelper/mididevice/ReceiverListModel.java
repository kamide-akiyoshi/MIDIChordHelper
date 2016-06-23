package camidion.chordhelper.mididevice;

import java.util.List;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.AbstractListModel;

/**
 * {@link Receiver} のリストを表す {@link javax.swing.ListModel}
 */
public class ReceiverListModel extends AbstractListModel<Receiver> {
	protected MidiDeviceModel deviceModel;
	public ReceiverListModel(MidiDeviceModel deviceModel) { this.deviceModel = deviceModel; }
	@Override
	public Receiver getElementAt(int index) {
		return deviceModel.getMidiDevice().getReceivers().get(index);
	}
	@Override
	public int getSize() {
		return deviceModel.getMidiDevice().getReceivers().size();
	}
	public int indexOf(Object element) {
		return deviceModel.getMidiDevice().getReceivers().indexOf(element);
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
	 * このリストモデルの{@link Receiver}に接続された他デバイスの{@link Transmitter}を全て閉じます。
	 */
	public void closePeerTransmitters() {
		List<Receiver> rxList = deviceModel.getMidiDevice().getReceivers();
		MidiDeviceModelList deviceModelList = deviceModel.getDeviceModelList();
		for( Receiver rx : rxList ) {
			for( MidiDeviceModel m : deviceModelList ) {
				if( m == deviceModel ) continue;
				TransmitterListModel txListModel = m.getTransmitterListModel();
				if( txListModel == null ) continue;
				txListModel.closePeerTransmitterOf(rx);
			}
		}
	}
}
