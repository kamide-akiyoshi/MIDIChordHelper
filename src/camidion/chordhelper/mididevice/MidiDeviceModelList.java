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

/**
 * すべてのMIDIデバイスモデル {@link MidiConnecterListModel} を収容するリストです。
 * {@link MidiDeviceTreeModel} もこのリストを参照します。
 */
public class MidiDeviceModelList extends Vector<MidiConnecterListModel> {
	/**
	 * ツリー表示のルートに使用するタイトル
	 */
	public static final String TITLE = "MIDI devices";
	/**
	 * MIDIエディタ
	 */
	public MidiSequenceEditor editorDialog;
	/**
	 * MIDIエディタモデル
	 */
	private MidiConnecterListModel editorDialogModel;
	/**
	 * MIDIシンセサイザーモデル
	 */
	private MidiConnecterListModel synthModel;
	/**
	 * 最初のMIDI出力
	 */
	private MidiConnecterListModel firstMidiOutModel;
	/**
	 * MIDIデバイスモデルリストを生成します。
	 * @param guiVirtualDeviceList GUI仮想MIDIデバイスのリスト
	 */
	public MidiDeviceModelList(List<VirtualMidiDevice> guiVirtualDeviceList) {
		MidiDevice.Info[] deviceInfos = MidiSystem.getMidiDeviceInfo();
		MidiConnecterListModel guiModels[] = new MidiConnecterListModel[guiVirtualDeviceList.size()];
		MidiConnecterListModel firstMidiInModel = null;
		//
		// GUI仮想MIDIデバイスリストの構築
		for( int i=0; i<guiVirtualDeviceList.size(); i++ )
			guiModels[i] = addMidiDevice(guiVirtualDeviceList.get(i));
		//
		// シーケンサの取得
		Sequencer sequencer;
		try {
			sequencer = MidiSystem.getSequencer(false);
			sequencerModel = (MidiSequencerModel)addMidiDevice(sequencer);
		} catch( MidiUnavailableException e ) {
			System.out.println(ChordHelperApplet.VersionInfo.NAME +" : MIDI sequencer unavailable");
			e.printStackTrace();
		}
		// MIDIエディタの生成
		editorDialog = new MidiSequenceEditor(sequencerModel);
		editorDialogModel = addMidiDevice(editorDialog.getVirtualMidiDevice());
		for( MidiDevice.Info info : deviceInfos ) {
			// MIDIデバイスの取得
			MidiDevice device;
			try {
				device = MidiSystem.getMidiDevice(info);
			} catch( MidiUnavailableException e ) {
				e.printStackTrace(); continue;
			}
			// シーケンサの場合はすでに取得済みなのでスキップ
			if( device instanceof Sequencer ) continue;
			//
			// Java内蔵シンセサイザ
			if( device instanceof Synthesizer ) {
				try {
					synthModel = addMidiDevice(MidiSystem.getSynthesizer());
				} catch( MidiUnavailableException e ) {
					System.out.println(ChordHelperApplet.VersionInfo.NAME +
							" : Java internal MIDI synthesizer unavailable");
					e.printStackTrace();
				}
				continue;
			}
			// MIDIデバイスを追加し、最初のエントリを覚えておく
			MidiConnecterListModel m = addMidiDevice(device);
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
			MidiConnecterListModel openModels[] = {
				synthModel,
				firstMidiOutModel,
				sequencerModel,
				firstMidiInModel,
				editorDialogModel,
			};
			for( MidiConnecterListModel m : openModels ) if( m != null ) m.openDevice();
			for( MidiConnecterListModel m : guiModels ) m.openDevice();
		} catch( MidiUnavailableException ex ) {
			ex.printStackTrace();
		}
		// 初期接続
		//
		for( MidiConnecterListModel mtx : guiModels ) {
			for( MidiConnecterListModel mrx : guiModels ) mtx.connectToReceiverOf(mrx);
			mtx.connectToReceiverOf(sequencerModel);
			mtx.connectToReceiverOf(synthModel);
			mtx.connectToReceiverOf(firstMidiOutModel);
		}
		if( firstMidiInModel != null ) {
			for( MidiConnecterListModel m : guiModels ) firstMidiInModel.connectToReceiverOf(m);
			firstMidiInModel.connectToReceiverOf(sequencerModel);
			firstMidiInModel.connectToReceiverOf(synthModel);
			firstMidiInModel.connectToReceiverOf(firstMidiOutModel);
		}
		if( sequencerModel != null ) {
			for( MidiConnecterListModel m : guiModels ) sequencerModel.connectToReceiverOf(m);
			sequencerModel.connectToReceiverOf(synthModel);
			sequencerModel.connectToReceiverOf(firstMidiOutModel);
		}
		if( editorDialogModel != null ) {
			editorDialogModel.connectToReceiverOf(synthModel);
			editorDialogModel.connectToReceiverOf(firstMidiOutModel);
		}
	}
	/**
	 * このデバイスモデルリストに登録されたMIDIシーケンサーモデルを返します。
	 * @return MIDIシーケンサーモデル
	 */
	public MidiSequencerModel getSequencerModel() { return sequencerModel; }
	private MidiSequencerModel sequencerModel;
	/**
	 * 指定のMIDIデバイスからMIDIデバイスモデルを生成して追加します。
	 * @param device MIDIデバイス
	 * @return 生成されたMIDIデバイスモデル
	 */
	private MidiConnecterListModel addMidiDevice(MidiDevice device) {
		MidiConnecterListModel m;
		if( device instanceof Sequencer )
			m = new MidiSequencerModel(this,(Sequencer)device,this);
		else
			m = new MidiConnecterListModel(device,this);
		addElement(m);
		return m;
	}
}