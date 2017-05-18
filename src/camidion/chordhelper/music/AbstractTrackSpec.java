package camidion.chordhelper.music;

import java.nio.charset.Charset;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequence;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;

/**
 * MIDIトラックの仕様を表すクラス
 */
public abstract class AbstractTrackSpec {
	public static final int BEAT_RESOLUTION = 2;
	// 最短の音符の長さ（四分音符を何回半分にするか）
	public String name = null;
	Track track = null;
	FirstTrackSpec firstTrackSpec = null;
	Sequence sequence = null;
	long minNoteTicks = 0;
	int preMeasures = 2;
	/**
	 * トラック名なしでMIDIトラック仕様を構築します。
	 */
	public AbstractTrackSpec() { }
	/**
	 * トラック名を指定してMIDIトラック仕様を構築します。
	 * @param name
	 */
	public AbstractTrackSpec(String name) {
		this.name = name;
	}
	/**
	 * このオブジェクトの文字列表現としてトラック名を返します。
	 * トラック名がない場合はスーパークラスの toString() と同じです。
	 */
	public String toString() {
		return name==null ? super.toString() : name;
	}
	/**
	 * トラックを生成して返します。
	 * @param seq MIDIシーケンス
	 * @param firstTrackSpec 最初のトラック仕様
	 * @return 生成したトラック
	 */
	public Track createTrack( Sequence seq, FirstTrackSpec firstTrackSpec, Charset charset ) {
		this.firstTrackSpec = firstTrackSpec;
		track = (sequence = seq).createTrack();
		if( name != null ) addStringTo( 0x03, name, charset, 0 );
		minNoteTicks = (long)( seq.getResolution() >> 2 );
		return track;
	}
	/**
	 * メタイベントを追加します。
	 * @param type メタイベントのタイプ
	 * @param data メタイベントのデータ
	 * @param tickPos tick位置
	 * @return {@link Track#add(MidiEvent)} と同じ
	 */
	public boolean addMetaEventTo( int type, byte data[], long tickPos  ) {
		MetaMessage metaMessage = new MetaMessage();
		try {
			metaMessage.setMessage( type, data, data.length );
		} catch( InvalidMidiDataException ex ) {
			ex.printStackTrace();
			return false;
		}
		return track.add(new MidiEvent(metaMessage, tickPos));
	}
	/**
	 * 文字列をメタイベントとして追加します。
	 * @param type メタイベントのタイプ
	 * @param str 追加する文字列
	 * @param tickPos tick位置
	 * @return {@link #addMetaEventTo(int, byte[], long)} と同じ
	 */
	public boolean addStringTo( int type, String str, Charset charset, long tickPos ) {
		if( str == null ) str = "";
		return addMetaEventTo(type, str.getBytes(charset), tickPos);
	}
	public boolean addStringTo( int type, ChordProgression.ChordStroke cs, Charset charset ) {
		return addStringTo(type, cs.chord.toString(), charset, cs.tickRange.startTickPos);
	}
	public boolean addStringTo( int type, ChordProgression.Lyrics lyrics, Charset charset ) {
		return addStringTo(type, lyrics.text, charset, lyrics.startTickPos);
	}
	public boolean addEOT( long tickPos ) {
		return addMetaEventTo( 0x2F, new byte[0], tickPos );
	}
	public void setChordSymbolText(ChordProgression cp, Charset charset) {
		cp.setChordSymbolTextTo(this, charset);
	}
	public boolean addSysEx(byte[] data, long tickPos) {
		SysexMessage msg = new SysexMessage();
		try {
			msg.setMessage( SysexMessage.SYSTEM_EXCLUSIVE, data, data.length );
		} catch( InvalidMidiDataException ex ) {
			ex.printStackTrace();
			return false;
		}
		return track.add(new MidiEvent( msg, tickPos ));
	}
}