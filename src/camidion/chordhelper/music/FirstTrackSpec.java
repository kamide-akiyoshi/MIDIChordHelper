package camidion.chordhelper.music;

import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

// 最初のトラック専用
//
public class FirstTrackSpec extends AbstractTrackSpec {
	static byte default_tempo_data[] = { 0x07, (byte)0xA1, 0x20 };	// 120[QPM]
	static byte default_timesig_data[] = { 0x04, 0x02, 0x18, 0x08 };	// 4/4
	byte tempo_data[] = default_tempo_data;
	byte timesig_data[] = default_timesig_data;
	Key key = null;
	long ticks_per_measure;
	public FirstTrackSpec() { }
	public FirstTrackSpec(String name) {
		this.name = name;
	}
	public FirstTrackSpec(
		String name, byte[] tempo_data, byte[] timesig_data
	) {
		this.name = name;
		if( tempo_data != null ) this.tempo_data = tempo_data;
		if( timesig_data != null ) this.timesig_data = timesig_data;
	}
	public FirstTrackSpec(
		String name, byte[] tempo_data, byte[] timesig_data, Key key
	) {
		this(name,tempo_data,timesig_data);
		this.key = key;
	}
	public Track createTrack(Sequence seq) {
		return createTrack( seq, 0, 0 );
	}
	public Track createTrack(
		Sequence seq, int start_measure_pos, int end_measure_pos
	) {
		this.pre_measures = start_measure_pos - 1;
		Track track = super.createTrack( seq, this );
		addTempo(
			this.tempo_data = (
				tempo_data == null ? default_tempo_data : tempo_data
			), 0
		);
		addTimeSignature(
			this.timesig_data = (
				timesig_data == null ? default_timesig_data : timesig_data
			), 0
		);
		if( key != null ) addKeySignature( key, 0 );
		ticks_per_measure = (long)(
			( 4 * seq.getResolution() * this.timesig_data[0] ) >> this.timesig_data[1]
		);
		addEOT( end_measure_pos * ticks_per_measure );
		return track;
	}
	public boolean addKeySignature( Key key, long tick_pos ) {
		return addMetaEventTo( 0x59, key.getBytes(), tick_pos );
	}
	public boolean addTempo( byte data[], long tick_pos ) {
		return addMetaEventTo( 0x51, data, tick_pos );
	}
	public boolean addTimeSignature( byte data[], long tick_pos ) {
		return addMetaEventTo( 0x58, data, tick_pos );
	}
}