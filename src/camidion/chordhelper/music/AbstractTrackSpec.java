package camidion.chordhelper.music;

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
	FirstTrackSpec first_track_spec = null;
	Sequence sequence = null;
	long minNoteTicks = 0;
	int pre_measures = 2;
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
	public Track createTrack( Sequence seq, FirstTrackSpec firstTrackSpec ) {
		this.first_track_spec = firstTrackSpec;
		track = (sequence = seq).createTrack();
		if( name != null ) addStringTo( 0x03, name, 0 );
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
		MetaMessage meta_msg = new MetaMessage();
		try {
			meta_msg.setMessage( type, data, data.length );
		} catch( InvalidMidiDataException ex ) {
			ex.printStackTrace();
			return false;
		}
		return track.add(new MidiEvent(meta_msg, tickPos));
	}
	/**
	 * 文字列をメタイベントとして追加します。
	 * @param type メタイベントのタイプ
	 * @param str 追加する文字列
	 * @param tickPos tick位置
	 * @return {@link #addMetaEventTo(int, byte[], long)} と同じ
	 */
	public boolean addStringTo( int type, String str, long tickPos ) {
		if( str == null ) str = "";
		return addMetaEventTo( type, str.getBytes(), tickPos );
	}
	public boolean addStringTo( int type, ChordProgression.ChordStroke cs ) {
		return addStringTo(type, cs.chord.toString(), cs.tick_range.start_tick_pos);
	}
	public boolean addStringTo( int type, ChordProgression.Lyrics lyrics ) {
		return addStringTo(type, lyrics.text, lyrics.start_tick_pos);
	}
	public boolean addEOT( long tick_pos ) {
		return addMetaEventTo( 0x2F, new byte[0], tick_pos );
	}
	public void setChordSymbolText( ChordProgression cp ) {
		cp.setChordSymbolTextTo( this );
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