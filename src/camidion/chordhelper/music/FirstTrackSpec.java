package camidion.chordhelper.music;

import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

// 最初のトラック専用
//
public class FirstTrackSpec extends AbstractTrackSpec {
	private static final byte DEFAULT_TEMPO_DATA[] = { 0x07, (byte)0xA1, 0x20 };	// 120[QPM]
	private static final byte DEFAULT_TIMESIG_DATA[] = { 0x04, 0x02, 0x18, 0x08 };	// 4/4
	byte tempoData[] = DEFAULT_TEMPO_DATA;
	byte timesigData[] = DEFAULT_TIMESIG_DATA;
	Key key = null;
	long ticksPerMeasure;
	public FirstTrackSpec() { }
	public FirstTrackSpec(String name) { this.name = name; }
	public FirstTrackSpec(String name, byte[] tempoData, byte[] timesigData) {
		this.name = name;
		if( tempoData != null ) this.tempoData = tempoData;
		if( timesigData != null ) this.timesigData = timesigData;
	}
	public FirstTrackSpec(String name, byte[] tempoData, byte[] timesigData, Key key) {
		this(name,tempoData,timesigData);
		this.key = key;
	}
	public Track createTrack(Sequence seq) {
		return createTrack( seq, 0, 0 );
	}
	public Track createTrack(Sequence seq, int startMeasurePos, int endMeasurePos) {
		preMeasures = startMeasurePos - 1;
		Track track = super.createTrack( seq, this );
		if( tempoData == null ) tempoData = DEFAULT_TEMPO_DATA;
		addTempo(tempoData, 0);
		if( timesigData == null ) timesigData = DEFAULT_TIMESIG_DATA;
		addTimeSignature(timesigData, 0);
		if( key != null ) addKeySignature( key, 0 );
		ticksPerMeasure = (long)(( 4 * seq.getResolution() * timesigData[0] ) >> timesigData[1]);
		addEOT( endMeasurePos * ticksPerMeasure );
		return track;
	}
	public boolean addKeySignature( Key key, long tickPos ) {
		return addMetaEventTo( 0x59, key.getBytes(), tickPos );
	}
	public boolean addTempo( byte data[], long tickPos ) {
		return addMetaEventTo( 0x51, data, tickPos );
	}
	public boolean addTimeSignature( byte data[], long tickPos ) {
		return addMetaEventTo( 0x58, data, tickPos );
	}
}
