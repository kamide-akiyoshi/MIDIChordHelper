package camidion.chordhelper.mididevice;

import java.util.List;
import java.util.Vector;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;
import javax.sound.midi.Transmitter;

import camidion.chordhelper.ChordHelperApplet;

/**
 * 仮想MIDIデバイスを含めたすべてのMIDIデバイスモデル {@link MidiDeviceModel} を収容するリスト
 */
public class MidiDeviceModelList extends Vector<MidiDeviceModel> {

	public String toString() { return "MIDI devices"; }

	private MidiSequencerModel sequencerModel;
	public MidiSequencerModel getSequencerModel() { return sequencerModel; }

	public MidiDeviceModelList(List<VirtualMidiDevice> guiVirtualDeviceList) {
		//
		// GUI仮想MIDIデバイス
		MidiDeviceModel guiModels[] = new MidiDeviceModel[guiVirtualDeviceList.size()];
		for( int i=0; i<guiVirtualDeviceList.size(); i++ ) {
			addElement(guiModels[i] = new MidiDeviceModel(guiVirtualDeviceList.get(i), this));
		}
		// シーケンサの取得
		Sequencer sequencer;
		try {
			sequencer = MidiSystem.getSequencer(false);
			addElement(sequencerModel = new MidiSequencerModel(sequencer, this));
		} catch( MidiUnavailableException e ) {
			System.out.println(ChordHelperApplet.VersionInfo.NAME +" : MIDI sequencer unavailable");
			e.printStackTrace();
		}
		// その他のリアルMIDIデバイスの取得
		MidiDeviceModel synthModel = null;
		MidiDeviceModel firstMidiInModel = null;
		MidiDeviceModel firstMidiOutModel = null;
		MidiDevice.Info[] deviceInfos = MidiSystem.getMidiDeviceInfo();
		for( MidiDevice.Info info : deviceInfos ) {
			MidiDevice device;
			try {
				device = MidiSystem.getMidiDevice(info);
			} catch( MidiUnavailableException e ) {
				e.printStackTrace(); continue;
			}
			// シーケンサはすでに取得済みなのでスキップ
			if( device instanceof Sequencer ) continue;
			//
			// Java内蔵シンセサイザ
			if( device instanceof Synthesizer ) {
				try {
					addElement(synthModel = new MidiDeviceModel(MidiSystem.getSynthesizer(), this));
				} catch( MidiUnavailableException e ) {
					System.out.println(ChordHelperApplet.VersionInfo.NAME +
							" : Java internal MIDI synthesizer unavailable");
					e.printStackTrace();
				}
				continue;
			}
			// その他のMIDIデバイス
			MidiDeviceModel m;
			addElement(m = new MidiDeviceModel(device, this));
			if( m.rxSupported() && firstMidiOutModel == null ) firstMidiOutModel = m;
			if( m.txSupported() && firstMidiInModel == null ) firstMidiInModel = m;
		}
		// MIDIデバイスを開く。
		//   NOTE: 必ず MIDI OUT Rx デバイスを先に開くこと。
		//
		//   そうすれば、後から開いた MIDI IN Tx デバイスからの
		//   タイムスタンプのほうが「若く」なる。これにより、
		//   先に開かれ「少し歳を食った」Rx デバイスは
		//   「信号が遅れてやってきた」と認識するので、
		//   遅れを取り戻そうとして即座に音を出してくれる。
		//
		//   開く順序が逆になると「進みすぎるから遅らせよう」として
		//   無用なレイテンシーが発生する原因になる。
		try {
			MidiDeviceModel openModels[] = {
				synthModel,
				firstMidiOutModel,
				sequencerModel,
				firstMidiInModel,
			};
			for( MidiDeviceModel m : openModels ) if( m != null ) m.open();
			for( MidiDeviceModel m : guiModels ) m.open();
			//
			// 初期接続
			MidiDeviceModel.TransmitterListModel txListModel;
			for( MidiDeviceModel mtx : guiModels ) {
				if( (txListModel = mtx.getTransmitterListModel() ) != null) {
					for( MidiDeviceModel m : guiModels ) txListModel.connectToFirstReceiverOfDevice(m);
					txListModel.connectToFirstReceiverOfDevice(sequencerModel);
					txListModel.connectToFirstReceiverOfDevice(synthModel);
					txListModel.connectToFirstReceiverOfDevice(firstMidiOutModel);
				}
			}
			if( firstMidiInModel != null && (txListModel = firstMidiInModel.getTransmitterListModel()) != null) {
				for( MidiDeviceModel m : guiModels ) txListModel.connectToFirstReceiverOfDevice(m);
				txListModel.connectToFirstReceiverOfDevice(sequencerModel);
				txListModel.connectToFirstReceiverOfDevice(synthModel);
				txListModel.connectToFirstReceiverOfDevice(firstMidiOutModel);
			}
			if( sequencerModel != null && (txListModel = sequencerModel.getTransmitterListModel()) != null) {
				for( MidiDeviceModel m : guiModels ) txListModel.connectToFirstReceiverOfDevice(m);
				txListModel.connectToFirstReceiverOfDevice(synthModel);
				txListModel.connectToFirstReceiverOfDevice(firstMidiOutModel);
			}
		} catch( MidiUnavailableException ex ) {
			ex.printStackTrace();
		}
	}
	/**
	 * {@link Transmitter}を持つすべてのデバイス（例：MIDIキーボードなど）について、
	 * {@link MidiDeviceModel#resetMicrosecondPosition()}でマイクロ秒位置をリセットします。
	 */
	public void resetMicrosecondPosition() {
		for(MidiDeviceModel m : this) {
			MidiDeviceModel.TransmitterListModel txListModel = m.getTransmitterListModel();
			if( txListModel != null ) txListModel.resetMicrosecondPosition();
		}
	}
	/**
	 * すべてのMIDIデバイスを閉じます。
	 */
	public void closeAllDevices() { for(MidiDeviceModel m : this) m.getMidiDevice().close(); }
}
