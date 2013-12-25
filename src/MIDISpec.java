import java.util.HashMap;
import java.util.Map;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
/**
 * MIDI仕様
 */
public class MIDISpec {
	public static final int MAX_CHANNELS = 16;
	public static final int PITCH_BEND_NONE = 8192;
	/**
	 * メタメッセージタイプ名マップ
	 */
	private static final Map<Integer,String>
		META_MESSAGE_TYPE_NAMES = new HashMap<Integer,String>() {
			{
				put(0x00, "Seq Number");
				put(0x01, "Text");
				put(0x02, "Copyright");
				put(0x03, "Seq/Track Name");
				put(0x04, "Instrument Name");
				put(0x05, "Lyric");
				put(0x06, "Marker");
				put(0x07, "Cue Point");
				put(0x08, "Program Name");
				put(0x09, "Device Name");
				put(0x20, "MIDI Ch.Prefix");
				put(0x21, "MIDI Output Port");
				put(0x2F, "End Of Track");
				put(0x51, "Tempo");
				put(0x54, "SMPTE Offset");
				put(0x58, "Time Signature");
				put(0x59, "Key Signature");
				put(0x7F, "Sequencer Specific");
			}
		};
	/**
	 * メタメッセージタイプの名前を返します。
	 * @param metaMessageType メタメッセージタイプ
	 * @return メタメッセージタイプの名前
	 */
	public static String getMetaName(int metaMessageType) {
		return META_MESSAGE_TYPE_NAMES.get(metaMessageType);
	}
	/**
	 * メタメッセージタイプがテキストのつくものか調べます。
	 * @param metaMessageType メタメッセージタイプ
	 * @return テキストがつくときtrue
	 */
	public static boolean hasMetaText(int metaMessageType) {
		return (metaMessageType > 0 && metaMessageType < 10);
	}
	/**
	 * メタメッセージタイプが拍子記号か調べます。
	 * @param metaMessageType メタメッセージタイプ
	 * @return 拍子記号ならtrue
	 */
	public static boolean isTimeSignature(int metaMessageType) {
		return metaMessageType == 0x58;
	}
	/**
	 * MIDIメッセージが拍子記号か調べます。
	 * @param msg MIDIメッセージ
	 * @return 拍子記号ならtrue
	 */
	public static boolean isTimeSignature(MidiMessage midiMessage) {
		if ( !(midiMessage instanceof MetaMessage) )
			return false;
		return isTimeSignature( ((MetaMessage)midiMessage).getType() );
	}
	/**
	 * メタメッセージタイプが EOT (End Of Track) か調べます。
	 * @param metaMessageType メタメッセージタイプ
	 * @return EOTならtrue
	 */
	public static boolean isEOT(int metaMessageType) {
		return metaMessageType == 0x2F;
	}
	/**
	 * MIDIメッセージが EOT (End Of Track) か調べます。
	 * @param midiMessage MIDIメッセージ
	 * @return EOTならtrue
	 */
	public static boolean isEOT(MidiMessage midiMessage) {
		if ( !(midiMessage instanceof MetaMessage) )
			return false;
		return isEOT( ((MetaMessage)midiMessage).getType() );
	}
	/**
	 * １分のマイクロ秒数
	 */
	public static final int MICROSECOND_PER_MINUTE = (60 * 1000 * 1000);
	/**
	 * MIDIのテンポメッセージについているバイト列をQPM単位のテンポに変換します。
	 * @param b バイト列
	 * @return テンポ[QPM]
	 */
	public static int byteArrayToQpmTempo(byte[] b) {
		int tempoInUsPerQuarter
			= ((b[0] & 0xFF) << 16) | ((b[1] & 0xFF) << 8) | (b[2] & 0xFF);
		return MICROSECOND_PER_MINUTE / tempoInUsPerQuarter;
	}
	/**
	 * QPM単位のテンポをMIDIのテンポメッセージ用バイト列に変換します。
	 * @param qpm テンポ[QPM]
	 * @return MIDIのテンポメッセージ用バイト列
	 */
	public static byte[] qpmTempoToByteArray(int qpm) {
		int tempoInUsPerQuarter = MICROSECOND_PER_MINUTE / qpm;
		byte[] b = new byte[3];
		b[0] = (byte)((tempoInUsPerQuarter >> 16) & 0xFF);
		b[1] = (byte)((tempoInUsPerQuarter >> 8) & 0xFF);
		b[2] = (byte)(tempoInUsPerQuarter & 0xFF);
		return b;
	}
	/**
	 * トラック名のバイト列を返します。
	 * @param track MIDIトラック
	 * @return トラック名のバイト列
	 */
	public static byte[] getNameBytesOf(Track track) {
		MidiEvent midiEvent;
		MidiMessage message;
		MetaMessage metaMessage;
		for( int i=0; i<track.size(); i++ ) {
			midiEvent = track.get(i);
			if( midiEvent.getTick() > 0 ) { // No more event at top, try next track
				break;
			}
			message = midiEvent.getMessage();
			if( ! (message instanceof MetaMessage) ) { // Not meta message
				continue;
			}
			metaMessage = (MetaMessage)message;
			if( metaMessage.getType() != 0x03 ) { // Not sequence name
				continue;
			}
			return metaMessage.getData();
		}
		return null;
	}
	/**
	 * トラック名のバイト列を設定します。
	 * @param track MIDIトラック
	 * @param name トラック名
	 * @return 成功：true、失敗：false
	 */
	public static boolean setNameBytesOf(Track track, byte[] name) {
		MidiEvent midiEvent = null;
		MidiMessage msg = null;
		MetaMessage metaMsg = null;
		for( int i=0; i<track.size(); i++ ) {
			if(
				(midiEvent = track.get(i)).getTick() > 0
				||
				(msg = midiEvent.getMessage()) instanceof MetaMessage
				&&
				(metaMsg = (MetaMessage)msg).getType() == 0x03
			) {
				break;
			}
			metaMsg = null;
		}
		if( metaMsg == null ) {
			if( name.length == 0 ) return false;
			track.add(new MidiEvent(
				(MidiMessage)(metaMsg = new MetaMessage()), 0
			));
		}
		try {
			metaMsg.setMessage(0x03, name, name.length);
		}
		catch( InvalidMidiDataException e ) {
			e.printStackTrace();
			return false;
		}
		return true;
	}
	/**
	 * シーケンス名のバイト列を返します。
	 * <p>トラック名の入った最初のトラックにあるトラック名を
	 * シーケンス名として返します。
	 * </p>
	 * @param seq MIDIシーケンス
	 * @return シーケンス名のバイト列
	 */
	public static byte[] getNameBytesOf(Sequence seq) {
		// Returns name of the MIDI sequence.
		// A sequence name is placed at top of first track of the sequence.
		//
		Track tracks[] = seq.getTracks();
		byte b[];
		for( Track track : tracks )
			if( (b = getNameBytesOf(track)) != null ) return b;
		return null;
	}
	/**
	 * シーケンス名のバイト列を設定します。
	 * <p>先頭のトラックに設定されます。
	 * 設定に失敗した場合、順に次のトラックへの設定を試みます。
	 * </p>
	 *
	 * @param seq MIDIシーケンス
	 * @param name シーケンス名のバイト列
	 * @return 成功：true、失敗：false
	 */
	public static boolean setNameBytesOf(Sequence seq, byte[] name) {
		Track tracks[] = seq.getTracks();
		for( Track track : tracks )
			if( setNameBytesOf(track,name) ) return true;
		return false;
	}
	///////////////////////////////////////////////////////////////////
	//
	// Channel Message / System Message
	//
	/**
	 * MIDIステータス名を返します。
	 * @param status MIDIステータス
	 * @return MIDIステータス名
	 */
	public static String getStatusName( int status ) {
		if( status < 0x80 ) {
			// No such status
			return null;
		}
		else if ( status < 0xF0 ) {
			// Channel Message
			return ch_msg_status_names[ (status >> 4) - 0x08 ];
		}
		else if ( status <= 0xFF ) {
			// System Message
			return sys_msg_names[ status - 0xF0 ];
		}
		return null;
	}
	/**
	 * 指定のMIDIショートメッセージがチャンネルメッセージかどうか調べます。
	 * @param msg MIDIメッセージ
	 * @return MIDIチャンネルメッセージの場合true
	 */
	public static boolean isChannelMessage( ShortMessage msg ) {
		return isChannelMessage( msg.getStatus() );
	}
	/**
	 * MIDIステータスがチャンネルメッセージかどうか調べます。
	 * @param status MIDIステータス
	 * @return MIDIチャンネルメッセージの場合true
	 */
	public static boolean isChannelMessage( int status ) {
		return ( status < 0xF0 && status >= 0x80 );
	}
	private static final String ch_msg_status_names[] = {
		// 0x80 - 0xE0 : Channel Voice Message
		// 0xB0 : Channel Mode Message
		"NoteOFF", "NoteON",
		"Polyphonic Key Pressure", "Ctrl/Mode",
		"Program", "Ch.Pressure", "Pitch Bend"
	};
	private static final String sys_msg_names[] = {
		// 0xF0 : System Exclusive
		"SysEx",
		//
		// 0xF1 - 0xF7 : System Common Message
		"MIDI Time Code Quarter Frame",
		"Song Position Pointer", "Song Select",
		null, null, "Tune Request", "Special SysEx",
		//
		// 0xF8 - 0xFF : System Realtime Message
		// 0xFF : Meta Message (SMF only, Not for wired MIDI message)
		"Timing Clock", null, "Start", "Continue",
		"Stop", null, "Active Sensing", "Meta / Sys.Reset",
	};
	///////////////////////////////////////////////////////////////////
	//
	// Control Change / Channel Mode Message
	//
	public static String getControllerName( int controller_number ) {
		if( controller_number < 0x00 ) {
			return null;
		}
		else if( controller_number < 0x20 ) {
			String s = controller_names_0[controller_number];
			if( s != null ) s += " (MSB)";
			return s;
		}
		else if( controller_number < 0x40 ) {
			String s = controller_names_0[controller_number - 0x20];
			if( s != null ) s += " (LSB)";
			return s;
		}
		else if( controller_number < 0x78 ) {
			return controller_momentary_switch_names[controller_number - 0x40];
		}
		else if( controller_number < 0x80 ) {
			return controller_mode_message_names[controller_number - 0x78];
		}
		else {
			return null;
		}
	}
	private static final String controller_names_0[] = {
		//
		// 0x00-0x0F (MSB)
		"Bank Select", "Modulation Depth", "Breath Controller", null,
		"Foot Controller", "Portamento Time", "Data Entry", "Volume",
		"Balance", null, "Pan", "Expression",
		"Effect Control 1", "Effect Control 2", null, null,
		//
		// 0x10-0x1F (MSB)
		"General Purpose 1", "General Purpose 2",
		"General Purpose 3", "General Purpose 4",
		null, null, null, null,
		null, null, null, null,
		null, null, null, null,
		//
		// 0x20-0x3F (LSB)
	};
	private static final String controller_momentary_switch_names[] = {
		//
		// 0x40-0x4F
		"Damper Pedal (Sustain)", "Portamento",
		"Sustenuto", "Soft Pedal",
		"Legato Footswitch", "Hold 2",
		"Sound Controller 1 (Sound Variation)",
		"Sound Controller 2 (Timbre/Harmonic Intens)",
		"Sound Controller 3 (Release Time)",
		"Sound Controller 4 (Attack Time)",
		"Sound Controller 5 (Brightness)",
		"Sound Controller 6 (Decay Time)",
		"Sound Controller 7 (Vibrato Rate)",
		"Sound Controller 8 (Vibrato Depth)",
		"Sound Controller 9 (Vibrato Delay)",
		"Sound Controller 10 (Undefined)",
		//
		// 0x50-0x5F
		"General Purpose 5", "General Purpose 6 (Temp Change)",
		"General Purpose 7", "General Purpose 8",
		"Portamento Control", null, null, null,
		null, null, null, "Reverb (Ext.Effects Depth)",
		"Tremelo Depth", "Chorus Depth",
		"Celeste (Detune) Depth", "Phaser Depth",
		//
		// 0x60-0x6F
		"Data Increment", "Data Decrement",
		"NRPN (LSB)", "NRPN (MSB)",
		"RPN (LSB)", "RPN (MSB)", null, null,
		null, null, null, null,
		null, null, null, null,
		//
		// 0x70-0x77
		null, null, null, null,
		null, null, null, null
	};
	private static final String controller_mode_message_names[] = {
		// 0x78-0x7F
		"All Sound OFF", "Reset All Controllers",
		"Local Control", "All Notes OFF",
		"Omni Mode OFF", "Omni Mode ON",
		"Mono Mode ON", "Poly Mode ON"
	};
	///////////////////////////////////////////////////////////////////
	//
	// System Exclusive
	//
	/**
	 * システムエクスクルーシブの製造者IDを名前に変換します。
	 * @param id 製造者ID
	 * @return 製造者名（不明な場合はnull）
	 */
	public static String getSysExManufacturerName( int id ) {
		switch( id ) {
		//
		// Manufacturer
		case 0x40: return "KAWAI";
		case 0x41: return "Roland";
		case 0x42: return "KORG";
		case 0x43: return "YAMAHA";
		case 0x44: return "CASIO";
		//
		// Special
		case 0x7D: return "Non-Commercial";
		case 0x7E: return "Universal: Non-RealTime";
		case 0x7F: return "Universal: RealTime";
		//
		default: return null;
		}
	}
	/**
	 * MIDIノート番号の最大値
	 */
	public static final int	MAX_NOTE_NO = 127;
	/**
	 * General MIDI の楽器ファミリー名の配列
	 */
	public static final String instrument_family_names[] = {

		"Piano",
		"Chrom.Percussion",
		"Organ",
		"Guitar",
		"Bass",
		"Strings",
		"Ensemble",
		"Brass",

		"Reed",
		"Pipe",
		"Synth Lead",
		"Synth Pad",
		"Synth Effects",
		"Ethnic",
		"Percussive",
		"Sound Effects",
	};
	/**
	 * General MIDI の楽器名（プログラムチェンジのプログラム名）の配列
	 */
	public static final String instrument_names[] = {
		"Acoustic Grand Piano",
		"Bright Acoustic Piano",
		"Electric Grand Piano",
		"Honky-tonk Piano",
		"Electric Piano 1",
		"Electric Piano 2",
		"Harpsichord",
		"Clavi",
		"Celesta",
		"Glockenspiel",
		"Music Box",
		"Vibraphone",
		"Marimba",
		"Xylophone",
		"Tubular Bells",
		"Dulcimer",
		"Drawbar Organ",
		"Percussive Organ",
		"Rock Organ",
		"Church Organ",
		"Reed Organ",
		"Accordion",
		"Harmonica",
		"Tango Accordion",
		"Acoustic Guitar (nylon)",
		"Acoustic Guitar (steel)",
		"Electric Guitar (jazz)",
		"Electric Guitar (clean)",
		"Electric Guitar (muted)",
		"Overdriven Guitar",
		"Distortion Guitar",
		"Guitar harmonics",
		"Acoustic Bass",
		"Electric Bass (finger)",
		"Electric Bass (pick)",
		"Fretless Bass",
		"Slap Bass 1",
		"Slap Bass 2",
		"Synth Bass 1",
		"Synth Bass 2",
		"Violin",
		"Viola",
		"Cello",
		"Contrabass",
		"Tremolo Strings",
		"Pizzicato Strings",
		"Orchestral Harp",
		"Timpani",
		"String Ensemble 1",
		"String Ensemble 2",
		"SynthStrings 1",
		"SynthStrings 2",
		"Choir Aahs",
		"Voice Oohs",
		"Synth Voice",
		"Orchestra Hit",
		"Trumpet",
		"Trombone",
		"Tuba",
		"Muted Trumpet",
		"French Horn",
		"Brass Section",
		"SynthBrass 1",
		"SynthBrass 2",
		"Soprano Sax",
		"Alto Sax",
		"Tenor Sax",
		"Baritone Sax",
		"Oboe",
		"English Horn",
		"Bassoon",
		"Clarinet",
		"Piccolo",
		"Flute",
		"Recorder",
		"Pan Flute",
		"Blown Bottle",
		"Shakuhachi",
		"Whistle",
		"Ocarina",
		"Lead 1 (square)",
		"Lead 2 (sawtooth)",
		"Lead 3 (calliope)",
		"Lead 4 (chiff)",
		"Lead 5 (charang)",
		"Lead 6 (voice)",
		"Lead 7 (fifths)",
		"Lead 8 (bass + lead)",
		"Pad 1 (new age)",
		"Pad 2 (warm)",
		"Pad 3 (polysynth)",
		"Pad 4 (choir)",
		"Pad 5 (bowed)",
		"Pad 6 (metallic)",
		"Pad 7 (halo)",
		"Pad 8 (sweep)",
		"FX 1 (rain)",
		"FX 2 (soundtrack)",
		"FX 3 (crystal)",
		"FX 4 (atmosphere)",
		"FX 5 (brightness)",
		"FX 6 (goblins)",
		"FX 7 (echoes)",
		"FX 8 (sci-fi)",
		"Sitar",
		"Banjo",
		"Shamisen",
		"Koto",
		"Kalimba",
		"Bag pipe",
		"Fiddle",
		"Shanai",
		"Tinkle Bell",
		"Agogo",
		"Steel Drums",
		"Woodblock",
		"Taiko Drum",
		"Melodic Tom",
		"Synth Drum",
		"Reverse Cymbal",
		"Guitar Fret Noise",
		"Breath Noise",
		"Seashore",
		"Bird Tweet",
		"Telephone Ring",
		"Helicopter",
		"Applause",
		"Gunshot",
	};
	/**
	 * パーカッション用MIDIノート番号の最小値
	 */
	public static final int	MIN_PERCUSSION_NUMBER = 35;
	/**
	 * パーカッション用のMIDIチャンネル（通常はCH.10）における
	 * ノート番号からパーカッション名を返します。
	 *
	 * @param note_no ノート番号
	 * @return パーカッション名
	 */
	public static String getPercussionName(int note_no) {
		int i = note_no - MIN_PERCUSSION_NUMBER ;
		return i>=0 && i < PERCUSSION_NAMES.length ? PERCUSSION_NAMES[i] : "(Unknown)" ;
	}
	public static final String	PERCUSSION_NAMES[] = {
		"Acoustic Bass Drum",
		"Bass Drum 1",
		"Side Stick",
		"Acoustic Snare",
		"Hand Clap",
		"Electric Snare",
		"Low Floor Tom",
		"Closed Hi Hat",
		"High Floor Tom",
		"Pedal Hi-Hat",
		"Low Tom",
		"Open Hi-Hat",
		"Low-Mid Tom",
		"Hi Mid Tom",
		"Crash Cymbal 1",
		"High Tom",
		"Ride Cymbal 1",
		"Chinese Cymbal",
		"Ride Bell",
		"Tambourine",
		"Splash Cymbal",
		"Cowbell",
		"Crash Cymbal 2",
		"Vibraslap",
		"Ride Cymbal 2",
		"Hi Bongo",
		"Low Bongo",
		"Mute Hi Conga",
		"Open Hi Conga",
		"Low Conga",
		"High Timbale",
		"Low Timbale",
		"High Agogo",
		"Low Agogo",
		"Cabasa",
		"Maracas",
		"Short Whistle",
		"Long Whistle",
		"Short Guiro",
		"Long Guiro",
		"Claves",
		"Hi Wood Block",
		"Low Wood Block",
		"Mute Cuica",
		"Open Cuica",
		"Mute Triangle",
		"Open Triangle",
	};
}
