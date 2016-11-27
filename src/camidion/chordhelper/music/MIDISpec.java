package camidion.chordhelper.music;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiMessage;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.SysexMessage;
import javax.sound.midi.Track;
/**
 * MIDI仕様（システムエクスクルーシブ含む）
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
	public static boolean hasMetaMessageText(int metaMessageType) {
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
		if ( !(midiMessage instanceof MetaMessage) ) return false;
		return isTimeSignature( ((MetaMessage)midiMessage).getType() );
	}
	/**
	 * メタメッセージタイプが EOT (End Of Track) か調べます。
	 * @param metaMessageType メタメッセージタイプ
	 * @return EOTならtrue
	 */
	public static boolean isEOT(int metaMessageType) { return metaMessageType == 0x2F; }
	/**
	 * MIDIメッセージが EOT (End Of Track) か調べます。
	 * @param midiMessage MIDIメッセージ
	 * @return EOTならtrue
	 */
	public static boolean isEOT(MidiMessage midiMessage) {
		if ( !(midiMessage instanceof MetaMessage) ) return false;
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
		int tempoInUsPerQuarter = ((b[0] & 0xFF) << 16) | ((b[1] & 0xFF) << 8) | (b[2] & 0xFF);
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
		for( Track track : tracks ) if( (b = getNameBytesOf(track)) != null ) return b;
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
		for( Track track : tracks ) if( setNameBytesOf(track,name) ) return true;
		return false;
	}
	/**
	 * シーケンスの名前や歌詞など、メタイベントのテキストをもとに文字コードを判定します。
	 * 判定できなかった場合はnullを返します。
	 * @param seq MIDIシーケンス
	 * @return 文字コード判定結果（またはnull）
	 */
	public static Charset getCharsetOf(Sequence seq) {
		Track tracks[] = seq.getTracks();
		byte[] b = new byte[0];
		for( Track track : tracks ) {
			MidiMessage message;
			MetaMessage metaMessage;
			for( int i=0; i<track.size(); i++ ) {
				message = track.get(i).getMessage();
				if( ! (message instanceof MetaMessage) ) continue;
				metaMessage = (MetaMessage)message;
				if( ! hasMetaMessageText(metaMessage.getType()) ) continue;
				byte[] additional = metaMessage.getData();
				byte[] concated = new byte[b.length + additional.length];
				System.arraycopy(b, 0, concated, 0, b.length);
				System.arraycopy(additional, 0, concated, b.length, additional.length);
				b = concated;
			}
		}
		if( b.length > 0 ) {
			try {
				String autoDetectedName = new String(b, "JISAutoDetect");
				Set<Map.Entry<String,Charset>> entrySet;
				entrySet = Charset.availableCharsets().entrySet();
				for( Map.Entry<String,Charset> entry : entrySet ) {
					Charset cs = entry.getValue();
					if( autoDetectedName.equals(new String(b, cs)) ) return cs;
				}
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		return null;
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
	/**
	 * コントロールチェンジの名前を返します。
	 * @param controllerNumber コントローラ番号
	 * @return コントロールチェンジの名前
	 */
	public static String getControllerName( int controllerNumber ) {
		if( controllerNumber < 0x00 ) {
			return null;
		}
		else if( controllerNumber < 0x20 ) {
			String s = controllerNames0[controllerNumber];
			if( s != null ) s += " (MSB)";
			return s;
		}
		else if( controllerNumber < 0x40 ) {
			String s = controllerNames0[controllerNumber - 0x20];
			if( s != null ) s += " (LSB)";
			return s;
		}
		else if( controllerNumber < 0x78 ) {
			return controllerMomentarySwitchNames[controllerNumber - 0x40];
		}
		else if( controllerNumber < 0x80 ) {
			return controllerModeMessageNames[controllerNumber - 0x78];
		}
		else {
			return null;
		}
	}
	private static final String controllerNames0[] = {
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
	private static final String controllerMomentarySwitchNames[] = {
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
	private static final String controllerModeMessageNames[] = {
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
	 * システムエクスクルーシブの製造者IDをキーにして製造者名を返すマップ
	 */
	public static final Map<Integer,String>
		SYSEX_MANUFACTURER_NAMES = new HashMap<Integer,String>() {
			{
				put(0x40,"KAWAI");
				put(0x41,"Roland");
				put(0x42,"KORG");
				put(0x43,"YAMAHA");
				put(0x44,"CASIO");
				put(0x7D,"Non-Commercial");
				put(0x7E,"Universal: Non-RealTime");
				put(0x7F,"Universal: RealTime");
			}
		};
	/**
	 * MIDIノート番号の最大値
	 */
	public static final int	MAX_NOTE_NO = 127;
	/**
	 * General MIDI の楽器ファミリー名の配列
	 */
	public static final String instrumentFamilyNames[] = {

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
	public static final String instrumentNames[] = {
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
	public static final String nsx39LyricElements[] = {
		"あ","い","う","え","お",
		"か","き","く","け","こ",
		"が","ぎ","ぐ","げ","ご",
	    "きゃ","きゅ","きょ",
	    "ぎゃ","ぎゅ","ぎょ",
		"さ","すぃ","す","せ","そ",
		"ざ","ずぃ","ず","ぜ","ぞ",
	    "しゃ","し","しゅ","しぇ","しょ",
	    "じゃ","じ","じゅ","じぇ","じょ",
		"た","てぃ","とぅ","て","と",
		"だ","でぃ","どぅ","で","ど",
		"てゅ","でゅ",
		"ちゃ","ち","ちゅ","ちぇ","ちょ",
		"つぁ","つぃ","つ","つぇ","つぉ",
		"な","に","ぬ","ね","の",
	    "にゃ","にゅ","にょ",
		"は","ひ","ふ","へ","ほ",
		"ば","び","ぶ","べ","ぼ",
		"ぱ","ぴ","ぷ","ぺ","ぽ",
		"ひゃ","ひゅ","ひょ",
		"びゃ","びゅ","びょ",
		"ぴゃ","ぴゅ","ぴょ",
		"ふぁ","ふぃ","ふゅ","ふぇ","ふぉ",
		"ま","み","む","め","も",
		"みゃ","みゅ","みょ",
		"や","ゆ","よ",
		"ら","り","る","れ","ろ",
		"りゃ","りゅ","りょ",
		"わ","うぃ","うぇ","を",
		"ん","ん","ん","ん","ん",
	};
	/**
	 * MIDIメッセージの内容を文字列で返します。
	 * @param msg MIDIメッセージ
	 * @param charset MIDIメタメッセージに含まれるテキストデータの文字コード
	 * @return MIDIメッセージの内容を表す文字列
	 */
	public static String msgToString(MidiMessage msg, Charset charset) {
		String str = "";
		if( msg instanceof ShortMessage ) {
			ShortMessage shortmsg = (ShortMessage)msg;
			int status = msg.getStatus();
			String statusName = getStatusName(status);
			int data1 = shortmsg.getData1();
			int data2 = shortmsg.getData2();
			if( isChannelMessage(status) ) {
				int channel = shortmsg.getChannel();
				String channelPrefix = "Ch."+(channel+1) + ": ";
				String statusPrefix = (
					statusName == null ? String.format("status=0x%02X",status) : statusName
				) + ": ";
				int cmd = shortmsg.getCommand();
				switch( cmd ) {
				case ShortMessage.NOTE_OFF:
				case ShortMessage.NOTE_ON:
					str += channelPrefix + statusPrefix + data1;
					str += ":[";
					if( MIDISpec.isRhythmPart(channel) ) {
						str += getPercussionName(data1);
					}
					else {
						str += NoteSymbol.noteNoToSymbol(data1);
					}
					str +="] Velocity=" + data2;
					break;
				case ShortMessage.POLY_PRESSURE:
					str += channelPrefix + statusPrefix + "Note=" + data1 + " Pressure=" + data2;
					break;
				case ShortMessage.PROGRAM_CHANGE:
					str += channelPrefix + statusPrefix + data1 + ":[" + instrumentNames[data1] + "]";
					if( data2 != 0 ) str += " data2=" + data2;
					break;
				case ShortMessage.CHANNEL_PRESSURE:
					str += channelPrefix + statusPrefix + data1;
					if( data2 != 0 ) str += " data2=" + data2;
					break;
				case ShortMessage.PITCH_BEND:
				{
					int val = ((data1 & 0x7F) | ((data2 & 0x7F) << 7));
					str += channelPrefix + statusPrefix + ( (val-8192) * 100 / 8191) + "% (" + val + ")";
				}
				break;
				case ShortMessage.CONTROL_CHANGE:
				{
					// Control / Mode message name
					String ctrl_name = getControllerName(data1);
					str += channelPrefix + (data1 < 0x78 ? "CtrlChg: " : "ModeMsg: ");
					if( ctrl_name == null ) {
						str += " No.=" + data1 + " Value=" + data2;
						return str;
					}
					str += ctrl_name;
					//
					// Controller's value
					switch( data1 ) {
					case 0x40: case 0x41: case 0x42: case 0x43: case 0x45:
						str += " " + ( data2==0x3F?"OFF":data2==0x40?"ON":data2 );
						break;
					case 0x44: // Legato Footswitch
						str += " " + ( data2==0x3F?"Normal":data2==0x40?"Legato":data2 );
						break;
					case 0x7A: // Local Control
						str += " " + ( data2==0x00?"OFF":data2==0x7F?"ON":data2 );
						break;
					default:
						str += " " + data2;
						break;
					}
				}
				break;

				default:
					// Never reached here
					break;
				}
			}
			else { // System Message
				str += (statusName == null ? ("status="+status) : statusName );
				str += " (" + data1 + "," + data2 + ")";
			}
			return str;
		}
		else if( msg instanceof MetaMessage ) {
			MetaMessage metamsg = (MetaMessage)msg;
			byte[] msgdata = metamsg.getData();
			int msgtype = metamsg.getType();
			str += "Meta: ";
			String meta_name = getMetaName(msgtype);
			if( meta_name == null ) {
				str += "Unknown MessageType="+msgtype + " Values=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				return str;
			}
			// Add the message type name
			str += meta_name;
			//
			// Add the text data
			if( hasMetaMessageText(msgtype) ) {
				str +=" ["+(new String(msgdata,charset))+"]";
				return str;
			}
			// Add the numeric data
			switch(msgtype) {
			case 0x00: // Sequence Number (for MIDI Format 2）
				if( msgdata.length == 2 ) {
					str += String.format(
						": %04X",
						((msgdata[0] & 0xFF) << 8) | (msgdata[1] & 0xFF)
					);
					break;
				}
				str += ": Size not 2 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x20: // MIDI Ch.Prefix
			case 0x21: // MIDI Output Port
				if( msgdata.length == 1 ) {
					str += String.format( ": %02X", msgdata[0] & 0xFF );
					break;
				}
				str += ": Size not 1 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x51: // Tempo
				str += ": " + byteArrayToQpmTempo( msgdata ) + "[QPM] (";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x54: // SMPTE Offset
				if( msgdata.length == 5 ) {
					str += ": "
						+ (msgdata[0] & 0xFF) + ":"
						+ (msgdata[1] & 0xFF) + ":"
						+ (msgdata[2] & 0xFF) + "."
						+ (msgdata[3] & 0xFF) + "."
						+ (msgdata[4] & 0xFF);
					break;
				}
				str += ": Size not 5 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x58: // Time Signature
				if( msgdata.length == 4 ) {
					str +=": " + msgdata[0] + "/" + (1 << msgdata[1]);
					str +=", "+msgdata[2]+"[clk/beat], "+msgdata[3]+"[32nds/24clk]";
					break;
				}
				str += ": Size not 4 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x59: // Key Signature
				if( msgdata.length == 2 ) {
					Key key = new Key(msgdata);
					str += ": " + key.signatureDescription();
					str += " (" + key.toStringIn(NoteSymbolLanguage.NAME) + ")";
					break;
				}
				str += ": Size not 2 byte : data=(";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			case 0x7F: // Sequencer Specific Meta Event
				str += " (";
				for( byte b : msgdata ) str += String.format( " %02X", b );
				str += " )";
				break;
			}
			return str;
		}
		else if( msg instanceof SysexMessage ) {
			SysexMessage sysexmsg = (SysexMessage)msg;
			int status = sysexmsg.getStatus();
			byte[] msgdata = sysexmsg.getData();
			int dataBytePos = 1;
			switch( status ) {
			case SysexMessage.SYSTEM_EXCLUSIVE:
				str += "SysEx: ";
				break;
			case SysexMessage.SPECIAL_SYSTEM_EXCLUSIVE:
				str += "SysEx(Special): ";
				break;
			default:
				str += "SysEx: Invalid (status="+status+") ";
				break;
			}
			if( msgdata.length < 1 ) {
				str += " Invalid data size: " + msgdata.length;
				return str;
			}
			int manufacturerId = (int)(msgdata[0] & 0xFF);
			int deviceId = (int)(msgdata[1] & 0xFF);
			int modelId = (int)(msgdata[2] & 0xFF);
			String manufacturerName = SYSEX_MANUFACTURER_NAMES.get(manufacturerId);
			if( manufacturerName == null ) {
				manufacturerName = String.format("[Manufacturer code %02X]", msgdata[0]);
			}
			str += manufacturerName + String.format(" (DevID=0x%02X)", deviceId);
			switch( manufacturerId ) {
			case 0x7E: // Non-Realtime Universal
				dataBytePos++;
				int sub_id_1 = modelId;
				int sub_id_2 = (int)(msgdata[3] & 0xFF);
				switch( sub_id_1 ) {
				case 0x09: // General MIDI (GM)
					switch( sub_id_2 ) {
					case 0x01: str += " GM System ON"; return str;
					case 0x02: str += " GM System OFF"; return str;
					}
					break;
				default:
					break;
				}
				break;
				// case 0x7F: // Realtime Universal
			case 0x41: // Roland
				dataBytePos++;
				switch( modelId ) {
				case 0x42:
					str += " [GS]"; dataBytePos++;
					if( msgdata[3]==0x12 ) {
						str += "DT1:"; dataBytePos++;
						switch( msgdata[4] ) {
						case 0x00:
							if( msgdata[5]==0x00 ) {
								if( msgdata[6]==0x7F ) {
									if( msgdata[7]==0x00 ) {
										str += " [88] System Mode Set (Mode 1: Single Module)"; return str;
									}
									else if( msgdata[7]==0x01 ) {
										str += " [88] System Mode Set (Mode 2: Double Module)"; return str;
									}
								}
							}
							else if( msgdata[5]==0x01 ) {
								int port = (msgdata[7] & 0xFF);
								str += String.format(
										" [88] Ch.Msg Rx Port: Block=0x%02X, Port=%s",
										msgdata[6],
										port==0?"A":port==1?"B":String.format("0x%02X",port)
										);
								return str;
							}
							break;
						case 0x40:
							if( msgdata[5]==0x00 ) {
								switch( msgdata[6] ) {
								case 0x00: str += " Master Tune: "; dataBytePos += 3; break;
								case 0x04: str += " Master Volume: "; dataBytePos += 3; break;
								case 0x05: str += " Master Key Shift: "; dataBytePos += 3; break;
								case 0x06: str += " Master Pan: "; dataBytePos += 3; break;
								case 0x7F:
									switch( msgdata[7] ) {
									case 0x00: str += " GS Reset"; return str;
									case 0x7F: str += " Exit GS Mode"; return str;
									}
									break;
								}
							}
							else if( msgdata[5]==0x01 ) {
								switch( msgdata[6] ) {
								// case 0x00: str += ""; break;
								// case 0x10: str += ""; break;
								case 0x30: str += " Reverb Macro: "; dataBytePos += 3; break;
								case 0x31: str += " Reverb Character: "; dataBytePos += 3; break;
								case 0x32: str += " Reverb Pre-LPF: "; dataBytePos += 3; break;
								case 0x33: str += " Reverb Level: "; dataBytePos += 3; break;
								case 0x34: str += " Reverb Time: "; dataBytePos += 3; break;
								case 0x35: str += " Reverb Delay FB: "; dataBytePos += 3; break;
								case 0x36: str += " Reverb Chorus Level: "; dataBytePos += 3; break;
								case 0x37: str += " [88] Reverb Predelay Time: "; dataBytePos += 3; break;
								case 0x38: str += " Chorus Macro: "; dataBytePos += 3; break;
								case 0x39: str += " Chorus Pre-LPF: "; dataBytePos += 3; break;
								case 0x3A: str += " Chorus Level: "; dataBytePos += 3; break;
								case 0x3B: str += " Chorus FB: "; dataBytePos += 3; break;
								case 0x3C: str += " Chorus Delay: "; dataBytePos += 3; break;
								case 0x3D: str += " Chorus Rate: "; dataBytePos += 3; break;
								case 0x3E: str += " Chorus Depth: "; dataBytePos += 3; break;
								case 0x3F: str += " Chorus Send Level To Reverb: "; dataBytePos += 3; break;
								case 0x40: str += " [88] Chorus Send Level To Delay: "; dataBytePos += 3; break;
								case 0x50: str += " [88] Delay Macro: "; dataBytePos += 3; break;
								case 0x51: str += " [88] Delay Pre-LPF: "; dataBytePos += 3; break;
								case 0x52: str += " [88] Delay Time Center: "; dataBytePos += 3; break;
								case 0x53: str += " [88] Delay Time Ratio Left: "; dataBytePos += 3; break;
								case 0x54: str += " [88] Delay Time Ratio Right: "; dataBytePos += 3; break;
								case 0x55: str += " [88] Delay Level Center: "; dataBytePos += 3; break;
								case 0x56: str += " [88] Delay Level Left: "; dataBytePos += 3; break;
								case 0x57: str += " [88] Delay Level Right: "; dataBytePos += 3; break;
								case 0x58: str += " [88] Delay Level: "; dataBytePos += 3; break;
								case 0x59: str += " [88] Delay FB: "; dataBytePos += 3; break;
								case 0x5A: str += " [88] Delay Send Level To Reverb: "; dataBytePos += 3; break;
								}
							}
							else if( msgdata[5]==0x02 ) {
								switch( msgdata[6] ) {
								case 0x00: str += " [88] EQ Low Freq: "; dataBytePos += 3; break;
								case 0x01: str += " [88] EQ Low Gain: "; dataBytePos += 3; break;
								case 0x02: str += " [88] EQ High Freq: "; dataBytePos += 3; break;
								case 0x03: str += " [88] EQ High Gain: "; dataBytePos += 3; break;
								}
							}
							else if( msgdata[5]==0x03 ) {
								if( msgdata[6] == 0x00 ) {
									str += " [Pro] EFX Type: "; dataBytePos += 3;
								}
								else if( msgdata[6] >= 0x03 && msgdata[6] <= 0x16 ) {
									str += String.format(" [Pro] EFX Param %d", msgdata[6]-2 );
									dataBytePos += 3;
								}
								else if( msgdata[6] == 0x17 ) {
									str += " [Pro] EFX Send Level To Reverb: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x18 ) {
									str += " [Pro] EFX Send Level To Chorus: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x19 ) {
									str += " [Pro] EFX Send Level To Delay: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1B ) {
									str += " [Pro] EFX Ctrl Src1: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1C ) {
									str += " [Pro] EFX Ctrl Depth1: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1D ) {
									str += " [Pro] EFX Ctrl Src2: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1E ) {
									str += " [Pro] EFX Ctrl Depth2: "; dataBytePos += 3;
								}
								else if( msgdata[6] == 0x1F ) {
									str += " [Pro] EFX Send EQ Switch: "; dataBytePos += 3;
								}
							}
							else if( (msgdata[5] & 0xF0) == 0x10 ) {
								int ch = (msgdata[5] & 0x0F);
								if( ch <= 9 ) ch--; else if( ch == 0 ) ch = 9;
								if( msgdata[6]==0x02 ) {
									str += String.format(
											" Rx Ch: Part=%d(0x%02X) Ch=0x%02X", (ch+1),  msgdata[5], msgdata[7]
											);
									return str;
								}
								else if( msgdata[6]==0x15 ) {
									String map;
									switch( msgdata[7] ) {
									case 0: map = " NormalPart"; break;
									case 1: map = " DrumMap1"; break;
									case 2: map = " DrumMap2"; break;
									default: map = String.format("0x%02X",msgdata[7]); break;
									}
									str += String.format(
										" Rhythm Part: Ch=%d(0x%02X) Map=%s",
										(ch+1), msgdata[5],
										map
									);
									return str;
								}
							}
							else if( (msgdata[5] & 0xF0) == 0x40 ) {
								int ch = (msgdata[5] & 0x0F);
								if( ch <= 9 ) ch--; else if( ch == 0 ) ch = 9;
								int dt = (msgdata[7] & 0xFF);
								if( msgdata[6]==0x20 ) {
									str += String.format(
										" [88] EQ: Ch=%d(0x%02X) %s",
										(ch+1), msgdata[5],
										dt==0 ? "OFF" : dt==1 ? "ON" : String.format("0x%02X",dt)
									);
								}
								else if( msgdata[6]==0x22 ) {
									str += String.format(
										" [Pro] Part EFX Assign: Ch=%d(0x%02X) %s",
										(ch+1), msgdata[5],
										dt==0 ? "ByPass" : dt==1 ? "EFX" : String.format("0x%02X",dt)
									);
								}
							}
							break;
						} // [4]
					} // [3] [DT1]
					break; // [GS]
				case 0x45:
					str += " [GS-LCD]"; dataBytePos++;
					if( msgdata[3]==0x12 ) {
						str += " [DT1]"; dataBytePos++;
						if( msgdata[4]==0x10 && msgdata[5]==0x00 && msgdata[6]==0x00 ) {
							dataBytePos += 3;
							str += " Disp [" +(new String(
								msgdata, dataBytePos, msgdata.length - dataBytePos - 2
							))+ "]";
						}
					} // [3] [DT1]
					break;
				case 0x14: str += " [D-50]"; dataBytePos++; break;
				case 0x16: str += " [MT-32]"; dataBytePos++; break;
				} // [2] model_id
				break;
			case 0x43: // Yamaha
				if( (deviceId & 0xF0) == 0x10 && modelId == 0x4C ) {
					str += " [XG]Dev#="+(deviceId & 0x0F);
					dataBytePos += 2;
					if( msgdata[3]==0 && msgdata[4]==0 && msgdata[5]==0x7E && msgdata[6]==0 ) {
						str += " System ON";
						return str;
					}

				}
				else if( deviceId == 0x79 && modelId == 9 ) {
					str += " [eVocaloid]";
					dataBytePos += 2;
					if( msgdata[3]==0x11 && msgdata[4]==0x0A && msgdata[5]==0 ) {
						StringBuilder p = new StringBuilder();
						for( int i=6; i<msgdata.length; i++ ) {
							int b = (msgdata[i] & 0xFF);
							if( b == 0xF7 ) break;
							String s = (
								b >= MIDISpec.nsx39LyricElements.length ?
								"?": MIDISpec.nsx39LyricElements[b]
							);
							p.append(s);
						}
						str += " pronounce["+p+"]";
						return str;
					}
				}
				break;
			default:
				break;
			}
			int i;
			str += " data=(";
			for( i = dataBytePos; i<msgdata.length-1; i++ ) {
				str += String.format( " %02X", msgdata[i] );
			}
			if( i < msgdata.length && (int)(msgdata[i] & 0xFF) != 0xF7 ) {
				str+=" [ Invalid EOX " + String.format( "%02X", msgdata[i] ) + " ]";
			}
			str += " )";
			return str;
		}
		byte[] msg_data = msg.getMessage();
		str += "(";
		for( byte b : msg_data ) {
			str += String.format( " %02X", b );
		}
		str += " )";
		return str;
	}
	public static boolean isRhythmPart(int ch) { return (ch == 9); }
}
