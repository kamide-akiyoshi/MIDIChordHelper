package camidion.chordhelper.midieditor;

import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;

/**
 *  MIDI シーケンスデータのtickインデックス
 * <p>拍子、テンポ、調だけを抜き出したトラックを保持するためのインデックスです。
 * 指定の MIDI tick の位置におけるテンポ、調、拍子を取得したり、
 * 拍子情報から MIDI tick と小節位置との間の変換を行うために使います。
 * </p>
 */
public class SequenceTickIndex {
	/**
	 * メタメッセージタイプ
	 */
	public static enum MetaMessageType {
		/** テンポ */
		TEMPO(0x51),
		/** 拍子 */
		TIME_SIGNATURE(0x58),
		/** 調号 */
		KEY_SIGNATURE(0x59);
		private MetaMessageType(int typeNumber) {this.typeNumber = typeNumber;}
		private int typeNumber;
		/**
		 * 指定されたメタメッセージがどのタイプに該当するかを返します。
		 * @param metaMessage メタメッセージ
		 * @return 該当するタイプ（見つからなければnull）
		 */
		public static MetaMessageType getByMessage(MetaMessage metaMessage) {
			int typeNumber = metaMessage.getType();
			for( MetaMessageType type : MetaMessageType.values() )
				if( type.typeNumber == typeNumber ) return type;
			return null;
		}
	}
	/**
	 * 新しいMIDIシーケンスデータのインデックスを構築します。
	 * @param sourceSequence 元のMIDIシーケンス
	 */
	public SequenceTickIndex(Sequence sourceSequence) {
		try {
			int ppq = sourceSequence.getResolution();
			wholeNoteTickLength = ppq * 4;
			Sequence indexSeq = new Sequence(Sequence.PPQ, ppq, MetaMessageType.values().length);
			Track[] tracks = indexSeq.getTracks();
			for( MetaMessageType type : MetaMessageType.values() ) {
				trackMap.put(type, tracks[type.ordinal()]);
			}
			Track[] sourceTracks = sourceSequence.getTracks();
			for( Track tk : sourceTracks ) {
				for( int i_evt = 0 ; i_evt < tk.size(); i_evt++ ) {
					MidiEvent evt = tk.get(i_evt);
					MidiMessage msg = evt.getMessage();
					if( ! (msg instanceof MetaMessage) ) continue;
					MetaMessageType type = MetaMessageType.getByMessage((MetaMessage)msg);
					if( type == null ) continue;
					trackMap.get(type).add(evt);
				}
			}
		}
		catch ( InvalidMidiDataException e ) {
			e.printStackTrace();
		}
		this.sourceSequence = sourceSequence;
	}
	/**
	 * メタメッセージタイプからトラックへの変換マップ
	 */
	private Map<MetaMessageType, Track> trackMap = new HashMap<>();
	/**
	 * 元のMIDIシーケンス
	 */
	private Sequence sourceSequence;
	/**
	 * 元のMIDIシーケンスを返します。
	 * @return 元のMIDIシーケンス
	 */
	public Sequence getSourceSequence() { return sourceSequence; }
	/**
	 * 指定されたtick位置以前の最後のメタメッセージを返します。
	 * @param type メタメッセージの種類
	 * @param tickPosition tick位置
	 * @return 指定されたtick位置以前の最後のメタメッセージ（見つからなければnull）
	 */
	public MetaMessage lastMetaMessageAt(MetaMessageType type, long tickPosition) {
		Track track = trackMap.get(type);
		for(int eventIndex = track.size()-1 ; eventIndex >= 0; eventIndex--) {
			MidiEvent event = track.get(eventIndex);
			if( event.getTick() > tickPosition )
				continue;
			MetaMessage metaMessage = (MetaMessage)(event.getMessage());
			if( metaMessage.getType() == 0x2F /* skip EOT (last event) */ )
				continue;
			return metaMessage;
		}
		return null;
	}

	private int wholeNoteTickLength;
	public int lastBeat;
	public int lastExtraTick;
	public byte timesigUpper;
	public byte timesigLowerIndex;
	/**
	 * tick位置を小節位置に変換します。
	 * @param tickPosition tick位置
	 * @return 小節位置
	 */
	public int tickToMeasure(long tickPosition) {
		byte extraBeats = 0;
		MidiEvent event = null;
		MidiMessage message = null;
		byte[] data = null;
		long currentTick = 0L;
		long nextTimesigTick = 0L;
		long prevTick = 0L;
		long duration = 0L;
		int lastMeasure = 0;
		int eventIndex = 0;
		timesigUpper = 4;
		timesigLowerIndex = 2; // =log2(4)
		Track tst = trackMap.get(MetaMessageType.TIME_SIGNATURE);
		if( tst != null ) {
			do {
				// Check current time-signature event
				if( eventIndex < tst.size() ) {
					message = (event = tst.get(eventIndex)).getMessage();
					currentTick = nextTimesigTick = event.getTick();
					if(currentTick > tickPosition || (message.getStatus() == 0xFF && ((MetaMessage)message).getType() == 0x2F /* EOT */)) {
						currentTick = tickPosition;
					}
				}
				else { // No event
					currentTick = nextTimesigTick = tickPosition;
				}
				// Add measure from last event
				//
				int beatTickLength = wholeNoteTickLength >> timesigLowerIndex;
				duration = currentTick - prevTick;
				int beats = (int)( duration / beatTickLength );
				lastExtraTick = (int)(duration % beatTickLength);
				int measures = beats / timesigUpper;
				extraBeats = (byte)(beats % timesigUpper);
				lastMeasure += measures;
				if( nextTimesigTick > tickPosition ) break;  // Not reached to next time signature
				//
				// Reached to the next time signature, so get it.
				if( ( data = ((MetaMessage)message).getData() ).length > 0 ) { // To skip EOT, check the data length.
					timesigUpper = data[0];
					timesigLowerIndex = data[1];
				}
				if( currentTick == tickPosition )  break;  // Calculation complete
				//
				// Calculation incomplete, so prepare for next
				//
				if( extraBeats > 0 ) {
					//
					// Extra beats are treated as 1 measure
					lastMeasure++;
				}
				prevTick = currentTick;
				eventIndex++;
			} while( true );
		}
		lastBeat = extraBeats;
		return lastMeasure;
	}
	/**
	 * 小節位置を MIDI tick に変換します。
	 * @param measure 小節位置
	 * @return MIDI tick
	 */
	public long measureToTick(int measure) {
		return measureToTick(measure, 0, 0);
	}
	/**
	 * 指定の小節位置、拍、拍内tickを、そのシーケンス全体の MIDI tick に変換します。
	 * @param measure 小節位置
	 * @param beat 拍
	 * @param extraTick 拍内tick
	 * @return そのシーケンス全体の MIDI tick
	 */
	public long measureToTick(int measure, int beat, int extraTick) {
		MidiEvent evt = null;
		MidiMessage msg = null;
		byte[] data = null;
		long tick = 0L;
		long prev_tick = 0L;
		long duration = 0L;
		long duration_sum = 0L;
		long estimated_ticks;
		int ticks_per_beat;
		int i_evt = 0;
		timesigUpper = 4;
		timesigLowerIndex = 2; // =log2(4)
		Track tst = trackMap.get(MetaMessageType.TIME_SIGNATURE);
		do {
			ticks_per_beat = wholeNoteTickLength >> timesigLowerIndex;
			estimated_ticks = ((measure * timesigUpper) + beat) * ticks_per_beat + extraTick;
			if( tst == null || i_evt > tst.size() ) {
				return duration_sum + estimated_ticks;
			}
			msg = (evt = tst.get(i_evt)).getMessage();
			if( msg.getStatus() == 0xFF && ((MetaMessage)msg).getType() == 0x2F /* EOT */ ) {
				return duration_sum + estimated_ticks;
			}
			duration = (tick = evt.getTick()) - prev_tick;
			if( duration >= estimated_ticks ) {
				return duration_sum + estimated_ticks;
			}
			// Re-calculate measure (ignore extra beats/ticks)
			measure -= ( duration / (ticks_per_beat * timesigUpper) );
			duration_sum += duration;
			//
			// Get next time-signature
			data = ( (MetaMessage)msg ).getData();
			timesigUpper = data[0];
			timesigLowerIndex = data[1];
			prev_tick = tick;
			i_evt++;
		} while( true );
	}
}