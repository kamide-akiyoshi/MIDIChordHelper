package camidion.chordhelper;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.MetaEventListener;
import javax.sound.midi.MetaMessage;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import camidion.chordhelper.mididevice.MidiSequencerModel;
import camidion.chordhelper.midieditor.SequenceTrackListTableModel;
import camidion.chordhelper.music.Chord;

public class ChordTextField extends JTextField implements MetaEventListener {
	private MidiSequencerModel sequencerModel;
	public ChordTextField(MidiSequencerModel sequencerModel) {
		super(80);
		//
		// JTextField は、サイズ設定をしないとリサイズ時に縦に伸び過ぎてしまう。
		// １行しか入力できないので、縦に伸びすぎるのはスペースがもったいない。
		// そこで、このような現象を防止するために、最大サイズを明示的に
		// 画面サイズと同じに設定する。
		//
		// To reduce resized height, set maximum size to screen size.
		//
		setMaximumSize(
			java.awt.Toolkit.getDefaultToolkit().getScreenSize()
		);
		this.sequencerModel = sequencerModel;
		sequencerModel.getSequencer().addMetaEventListener(this);
	}
	@Override
	public void meta(MetaMessage msg) {
		int t = msg.getType();
		switch(t) {
		case 0x01: // Text（任意のテキスト：コメントなど）
		case 0x05: // Lyrics（歌詞）
		case 0x02: // Copyright（著作権表示）
		case 0x03: // Sequence Name / Track Name（曲名またはトラック名）
		case 0x06: // Marker
			byte[] d = msg.getData();
			if( ! SwingUtilities.isEventDispatchThread() ) {
				// MIDIシーケンサの EDT から呼ばれた場合、
				// 表示処理を Swing の EDT に振り直す。
				SwingUtilities.invokeLater(new AddTextJob(t,d));
				return;
			}
			addText(t,d);
			break;
		default:
			return;
		}
	}
	/**
	 * 歌詞を追加するジョブ
	 */
	public class AddTextJob implements Runnable {
		private int type;
		private byte[] data;
		public AddTextJob(int type, byte[] data) {
			this.type = type;
			this.data = data;
		}
		@Override
		public void run() { addText(type, data); }
	}
	/**
	 * 前回のタイムスタンプ
	 */
	private long lastArrivedTime = System.nanoTime();
	/**
	 * スキップするテキスト
	 */
	private Map<Integer,String> skippingTextMap = new HashMap<>();
	/**
	 * テキストを追加し、カーソルを末尾に移動します。
	 * @param data テキストの元データ
	 */
	private void addText(int type, byte[] data) {
		// 頻繁に来たかどうかだけとりあえずチェック
		long arrivedTime = System.nanoTime();
		boolean isSoon = (arrivedTime - lastArrivedTime < 1000000000L /* 1sec */);
		lastArrivedTime = arrivedTime;
		//
		// 文字コード確認用シーケンス
		SequenceTrackListTableModel m = sequencerModel.getSequenceTrackListTableModel();
		//
		// 追加するデータを適切な文字コードで文字列に変換
		String additionalText;
		if( m != null ) {
			additionalText = new String(data,m.charset);
		}
		else try {
			additionalText = new String(data,"JISAutoDetect");
		}
		catch( UnsupportedEncodingException e ) {
			additionalText = new String(data);
		}
		additionalText = additionalText.trim();
		String lastAdditionalText = skippingTextMap.remove(type);
		// 歌詞とテキストで同じもの同士がすぐに来た場合は追加しない
		if( ! (isSoon && additionalText.equals(lastAdditionalText)) ) {
			// テキストと歌詞が同じかどうかチェックするための比較対象を記録
			switch(type) {
			case 0x01: skippingTextMap.put(0x05,additionalText);
			case 0x05: skippingTextMap.put(0x01,additionalText);
			}
			// 既存の歌詞
			String currentText = getText();
			if(
				currentText != null && ! currentText.isEmpty()
				&& (
					isSoon ||
					! additionalText.isEmpty() && additionalText.length() <= 8
				)
			) {
				// 既存歌詞がある場合、頻繁に来たか短い歌詞だったら追加
				currentText += " " + additionalText;
			}
			else {
				// それ以外の場合は上書き
				currentText = additionalText;
			}
			setText(currentText);
		}
		// 入力カーソル（キャレット）をテキストの末尾へ
		setCaretPosition(getText().length());
	}
	/**
	 * 現在のコード
	 */
	private Chord currentChord = null;
	/**
	 * コードを追加します。
	 * @param chord コード
	 */
	public void appendChord(Chord chord) {
		if( currentChord == null && chord == null )
			return;
		if( currentChord != null && chord != null && chord.equals(currentChord) )
			return;
		String delimiter = ""; // was "\n"
		setText( getText() + (chord == null ? delimiter : chord + " ") );
		currentChord = ( chord == null ? null : chord.clone() );
	}
}