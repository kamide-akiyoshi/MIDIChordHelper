package camidion.chordhelper.mididevice;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Transmitter;
import javax.swing.tree.TreePath;

/**
 * １個の{@link MidiDevice}を表すモデル
 */
public class MidiDeviceModel {
	/**
	 * {@link javax.swing.JTree}で使用するツリー表示のパスを返します。
	 * @return ツリーパス
	 */
	public TreePath getTreePath() { return treePath; }
	private TreePath treePath;
	/**
	 * このリストのMIDIデバイスの入出力タイプを返します。
	 * @return このリストのMIDIデバイスの入出力タイプ
	 */
	public MidiDeviceInOutType getMidiDeviceInOutType() { return ioType; }
	private MidiDeviceInOutType ioType;
	/**
	 * 対象MIDIデバイスを返します。
	 * @return 対象MIDIデバイス
	 */
	public MidiDevice getMidiDevice() { return device; }
	protected MidiDevice device;
	/**
	 * 対象MIDIデバイスの名前を返します。
	 */
	@Override
	public String toString() { return device.getDeviceInfo().toString(); }
	/**
	 * {@link Transmitter} のリストモデルを返します。サポートしていない場合はnullを返します。
	 * @return リストモデルまたはnull
	 */
	public TransmitterListModel getTransmitterListModel() { return txListModel; }
	private TransmitterListModel txListModel;
	/**
	 * {@link Receiver} のリストモデルを返します。サポートしていない場合はnullを返します。
	 * @return リストモデルまたはnull
	 */
	public ReceiverListModel getReceiverListModel() { return rxListModel; }
	private ReceiverListModel rxListModel;
	/**
	 * このMIDIデバイスモデルを収容しているリストを返します。
	 */
	public MidiDeviceModelList getDeviceModelList() { return deviceModelList; }
	protected MidiDeviceModelList deviceModelList;
	/**
	 * MIDIデバイスモデルを構築します。
	 *
	 * @param device 対象MIDIデバイス
	 * @param deviceModelList このMIDIデバイスモデルを収容しているリスト（接続相手となりうるMIDIデバイス）
	 */
	public MidiDeviceModel(MidiDevice device, MidiDeviceModelList deviceModelList) {
		this.device = device;
		this.deviceModelList = deviceModelList;
		if( device.getMaxTransmitters() != 0 ) txListModel = new TransmitterListModel(this);
		if( device.getMaxReceivers() != 0 ) {
			rxListModel = new ReceiverListModel(this);
			ioType = txListModel != null ? MidiDeviceInOutType.MIDI_IN_OUT :MidiDeviceInOutType.MIDI_OUT;
		}
		else {
			ioType = txListModel != null ? MidiDeviceInOutType.MIDI_IN :MidiDeviceInOutType.MIDI_NONE;
		}
		treePath = new TreePath(new Object[] {deviceModelList, ioType ,this});
	}
	/**
	 * このMIDIデバイスモデルを開きます。
	 * MIDIデバイスを {@link MidiDevice#open()} で開き、
	 * レシーバをサポートしている場合は {@link MidiDevice#getReceiver()} でレシーバを1個開きます。
	 *
	 * @throws MidiUnavailableException リソースの制約のためにデバイス開けない、
	 * またはレシーバを使用できない場合にスローされる。
	 */
	public void open() throws MidiUnavailableException {
		device.open();
		if( rxListModel != null ) rxListModel.openSingleReceiver();
	}
	/**
	 * このMIDIデバイスを {@link MidiDevice#close()} で閉じます。
	 * このMIDIデバイスの{@link Receiver}に接続している他デバイスの{@link Transmitter}があれば、
	 * それらも全て閉じます。
	 */
	public void close() {
		if( rxListModel != null ) rxListModel.closePeerTransmitters();
		device.close();
	}
}