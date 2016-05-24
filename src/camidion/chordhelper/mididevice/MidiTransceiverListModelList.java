package camidion.chordhelper.mididevice;

import java.util.List;
import java.util.Vector;

import javax.sound.midi.MidiDevice;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Synthesizer;

import camidion.chordhelper.ChordHelperApplet;
import camidion.chordhelper.midieditor.MidiSequenceEditor;
import camidion.chordhelper.midieditor.PlaylistTableModel;

/**
 * 仮想MIDIデバイスを含めたすべてのMIDIデバイスモデル {@link MidiTransceiverListModel} を収容するリスト
 */
public class MidiTransceiverListModelList extends Vector<MidiTransceiverListModel> {

	public String toString() { return "MIDI devices"; }

	private MidiSequenceEditor editorDialog;
	public MidiSequenceEditor getEditorDialog() { return editorDialog; }

	private MidiSequencerModel sequencerModel;
	public MidiSequencerModel getSequencerModel() { return sequencerModel; }

	public MidiTransceiverListModelList(List<VirtualMidiDevice> guiVirtualDeviceList) {
		//
		// GUI仮想MIDIデバイスリストの構築
		MidiTransceiverListModel guiModels[] = new MidiTransceiverListModel[guiVirtualDeviceList.size()];
		for( int i=0; i<guiVirtualDeviceList.size(); i++ ) {
			addElement(guiModels[i] = new MidiTransceiverListModel(guiVirtualDeviceList.get(i), this));
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
		// MIDIエディタの生成
		editorDialog = new MidiSequenceEditor(new PlaylistTableModel(sequencerModel));
		MidiTransceiverListModel eventTableDeviceModel;
		addElement(eventTableDeviceModel = new MidiTransceiverListModel(editorDialog.getEventTableMidiDevice(), this));
		MidiTransceiverListModel synthModel = null;
		MidiTransceiverListModel firstMidiInModel = null;
		MidiTransceiverListModel firstMidiOutModel = null;
		MidiDevice.Info[] deviceInfos = MidiSystem.getMidiDeviceInfo();
		for( MidiDevice.Info info : deviceInfos ) {
			// MIDIデバイスの取得
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
					addElement(synthModel = new MidiTransceiverListModel(MidiSystem.getSynthesizer(), this));
				} catch( MidiUnavailableException e ) {
					System.out.println(ChordHelperApplet.VersionInfo.NAME +
							" : Java internal MIDI synthesizer unavailable");
					e.printStackTrace();
				}
				continue;
			}
			// その他のMIDIデバイス
			MidiTransceiverListModel m;
			addElement(m = new MidiTransceiverListModel(device, this));
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
			MidiTransceiverListModel openModels[] = {
				synthModel,
				firstMidiOutModel,
				sequencerModel,
				firstMidiInModel,
				eventTableDeviceModel,
			};
			for( MidiTransceiverListModel m : openModels ) if( m != null ) {
				m.getMidiDevice().open();
				m.openReceiver();
			}
			for( MidiTransceiverListModel m : guiModels ) {
				m.getMidiDevice().open();
				m.openReceiver();
			}
		} catch( MidiUnavailableException ex ) {
			ex.printStackTrace();
		}
		// 初期接続
		//
		for( MidiTransceiverListModel mtx : guiModels ) {
			for( MidiTransceiverListModel mrx : guiModels ) mtx.connectToReceiverOf(mrx);
			mtx.connectToReceiverOf(sequencerModel);
			mtx.connectToReceiverOf(synthModel);
			mtx.connectToReceiverOf(firstMidiOutModel);
		}
		if( firstMidiInModel != null ) {
			for( MidiTransceiverListModel m : guiModels ) firstMidiInModel.connectToReceiverOf(m);
			firstMidiInModel.connectToReceiverOf(sequencerModel);
			firstMidiInModel.connectToReceiverOf(synthModel);
			firstMidiInModel.connectToReceiverOf(firstMidiOutModel);
		}
		if( sequencerModel != null ) {
			for( MidiTransceiverListModel m : guiModels ) sequencerModel.connectToReceiverOf(m);
			sequencerModel.connectToReceiverOf(synthModel);
			sequencerModel.connectToReceiverOf(firstMidiOutModel);
		}
		if( eventTableDeviceModel != null ) {
			eventTableDeviceModel.connectToReceiverOf(synthModel);
			eventTableDeviceModel.connectToReceiverOf(firstMidiOutModel);
		}
	}
}