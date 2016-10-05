package camidion.chordhelper.mididevice;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Receiver;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;

import camidion.chordhelper.ChordHelperApplet;

/**
 * 仮想MIDIデバイスを含めたすべてのMIDIデバイスモデル {@link MidiDeviceModel} を管理するオブジェクト
 */
public class MidiDeviceModelManager {

	private List<MidiDeviceModel> deviceModelList = new Vector<>();
	public List<MidiDeviceModel> getDeviceModelList() { return deviceModelList; }

	private MidiSequencerModel sequencerModel;
	public MidiSequencerModel getSequencerModel() { return sequencerModel; }

	private MidiDeviceTreeModel treeModel;
	public MidiDeviceTreeModel getTreeModel() { return treeModel; }
	public void setTreeModel(MidiDeviceTreeModel treeModel) { this.treeModel = treeModel; }

	public MidiDeviceModelManager(VirtualMidiDevice guiVirtualDevice) {
		List<MidiDevice.Info> deviceInfos = Arrays.asList(MidiSystem.getMidiDeviceInfo());
		//
		// GUI仮想MIDIデバイス
		MidiDeviceModel guiModel = new MidiDeviceModel(guiVirtualDevice, this);
		deviceModelList.add(guiModel);
		//
		// シーケンサ
		Sequencer sequencer = null;
		try {
			sequencer = MidiSystem.getSequencer(false);
			deviceModelList.add(sequencerModel = new MidiSequencerModel(sequencer, this));
		} catch( MidiUnavailableException e ) {
			System.out.println(ChordHelperApplet.VersionInfo.NAME +" : MIDI sequencer unavailable");
			e.printStackTrace();
		}
		// その他のリアルMIDIデバイス
		MidiDeviceModel synthModel = null;
		MidiDeviceModel firstMidiInModel = null;
		MidiDeviceModel firstMidiOutModel = null;
		for( MidiDevice.Info info : deviceInfos ) {
			MidiDevice device;
			try {
				device = MidiSystem.getMidiDevice(info);
			} catch( MidiUnavailableException e ) {
				e.printStackTrace();
				continue;
			}
			// シーケンサはすでに取得済みなのでスキップ
			if( device instanceof Sequencer ) continue;
			//
			// Java内蔵シンセサイザ
			if( device instanceof Synthesizer ) {
				try {
					device = MidiSystem.getSynthesizer();
					deviceModelList.add(synthModel = new MidiDeviceModel(device, this));
				} catch( MidiUnavailableException e ) {
					System.out.println(ChordHelperApplet.VersionInfo.NAME +
							" : Java internal MIDI synthesizer unavailable");
					e.printStackTrace();
				}
				continue;
			}
			// その他のMIDIデバイス
			MidiDeviceModel m;
			deviceModelList.add(m = new MidiDeviceModel(device, this));
			//
			// 最初の MIDI OUT（Windowsの場合は通常、内蔵音源 Microsoft GS Wavetable SW Synth）
			if( firstMidiOutModel == null && m.getReceiverListModel() != null ) firstMidiOutModel = m;
			//
			// 最初の MIDI IN（USB MIDI インターフェースにつながったMIDIキーボードなど）
			if( firstMidiInModel == null && m.getTransmitterListModel() != null ) firstMidiInModel = m;
		}
		// MIDIデバイスを開く。
		//   NOTE: 必ず MIDI OUT Rx デバイスを先に開くこと。
		//
		//   そうすれば、後から開いた MIDI IN Tx デバイスからのタイムスタンプのほうが「若く」なるので、
		//   相手の MIDI OUT Rx デバイスは「信号が遅れてやってきた」と認識、遅れを取り戻そうとして
		//   即座に音を出してくれる。
		//
		//   開く順序が逆になると「進みすぎるから遅らせよう」として無用なレイテンシーが発生する原因になる。
		try {
			// デバイスを開く
			MidiDeviceModel modelsToOpen[] = {synthModel, firstMidiOutModel, sequencerModel, firstMidiInModel};
			for( MidiDeviceModel m : modelsToOpen ) if( m != null ) m.open();
			guiModel.open();
			//
			// 初期接続
			// GUI → GUI、各音源、シーケンサ
			TransmitterListModel txListModel;
			if( (txListModel = guiModel.getTransmitterListModel() ) != null) {
				txListModel.connectToFirstReceiverOfDevices(guiModel,sequencerModel,synthModel,firstMidiOutModel);
			}
			// MIDI IN → GUI、各音源、シーケンサ
			if( firstMidiInModel != null && (txListModel = firstMidiInModel.getTransmitterListModel()) != null) {
				txListModel.connectToFirstReceiverOfDevices(guiModel,sequencerModel,synthModel,firstMidiOutModel);
			}
			// シーケンサ → GUI、各音源
			if( sequencerModel != null && (txListModel = sequencerModel.getTransmitterListModel()) != null) {
				txListModel.connectToFirstReceiverOfDevices(guiModel,synthModel,firstMidiOutModel);
			}
		} catch( MidiUnavailableException ex ) {
			ex.printStackTrace();
		}
		treeModel = new MidiDeviceTreeModel(deviceModelList);
	}
	/**
	 * MIDIデバイスリストを最新の状態に更新します。USB-MIDIデバイスの着脱後に使います。
	 */
	public void updateMidiDeviceList() {
		//
		// USBから抜いたMIDIデバイスを相手のMIDIデバイスに接続したまま
		// MidiSystem.getMidiDeviceInfo()を呼ぶと、Java VM が落ちることがある。
		//
		// それを回避するため、接続を一旦全部閉じる。
		//
		// 各Receiverごとに相手のTransmitterを閉じ、デバイスモデル同士の接続を覚えておく。
		Map<MidiDeviceModel, Set<MidiDeviceModel>> rxToTxConnections = new HashMap<>();
		for(MidiDeviceModel m : deviceModelList) {
			ReceiverListModel rxListModel = m.getReceiverListModel();
			if( rxListModel == null ) continue;
			Set<MidiDeviceModel> txDeviceModels = rxListModel.closePeerTransmitters();
			if( txDeviceModels.isEmpty() ) continue;
			rxToTxConnections.put(m, txDeviceModels);
		}
		// 最新のMIDIデバイス情報を取得
		MidiDevice.Info[] infoArray = MidiSystem.getMidiDeviceInfo();
		//
		// 追加されたデバイスの情報
		List<MidiDevice.Info> additionalDeviceInfos = new Vector<>(Arrays.asList(infoArray));
		//
		// 取り外されたデバイスのモデル
		List<MidiDeviceModel> removingDeviceModels = new Vector<>();
		//
		// 既存デバイスをスキャン
		for(MidiDeviceModel m : deviceModelList) {
			MidiDevice device = m.getMidiDevice();
			if( device instanceof VirtualMidiDevice ) continue;
			if( ! additionalDeviceInfos.remove(device.getDeviceInfo()) ) {
				// 最新のMIDIデバイス情報リストから除去しようとして、すでに存在していなかった場合、
				// そのデバイスはすでに取り外されている
				m.close();
				removingDeviceModels.add(m);
			}
		}
		// 取り外されたデバイスのモデルを除去
		deviceModelList.removeAll(removingDeviceModels);
		//
		// 追加されたデバイスをモデル化してデバイスモデルリストに追加
		for( MidiDevice.Info info : additionalDeviceInfos ) {
			try {
				MidiDevice device = MidiSystem.getMidiDevice(info);
				MidiDeviceModel m = new MidiDeviceModel(device, this);
				deviceModelList.add(m);
			} catch( MidiUnavailableException e ) {
				e.printStackTrace();
			}
		}
		// デバイスモデル同士の接続を復元
		Set<MidiDeviceModel> rxDeviceModels = rxToTxConnections.keySet();
		for( MidiDeviceModel rxm : rxDeviceModels ) {
			if( ! deviceModelList.contains(rxm) ) continue;
			List<Receiver> rxList = rxm.getReceiverListModel().getTransceivers();
			for( Receiver rx : rxList ) {
				Set<MidiDeviceModel> txDeviceModels = rxToTxConnections.get(rxm);
				for( MidiDeviceModel txm : txDeviceModels ) {
					try {
						txm.getTransmitterListModel().openTransmitter().setReceiver(rx);
					} catch( MidiUnavailableException e ) {
						e.printStackTrace();
					}
				}
			}
		}
		// デバイスツリーの更新を通知
		treeModel.setDeviceModelList(deviceModelList);
	}
	/**
	 * {@link Transmitter}を持つすべてのデバイス（例：MIDIキーボードなど）について、
	 * {@link MidiDeviceModel#resetMicrosecondPosition()}でマイクロ秒位置をリセットします。
	 */
	public void resetMicrosecondPosition() {
		for(MidiDeviceModel m : deviceModelList) {
			TransmitterListModel txListModel = m.getTransmitterListModel();
			if( txListModel != null ) txListModel.resetMicrosecondPosition();
		}
	}
	/**
	 * すべてのMIDIデバイスを閉じます。
	 */
	public void close() {
		for(MidiDeviceModel m : deviceModelList) m.getMidiDevice().close();
		deviceModelList.clear();
		treeModel = null;
	}
}
