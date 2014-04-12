package camidion.chordhelper.music;

import java.awt.Color;
import java.util.Arrays;
import java.util.Vector;

import javax.swing.JLabel;

/**
 * 和音（コード - musical chord）のクラス
 */
public class Chord implements Cloneable {
	/**
	 * コード構成音の順序に対応する色
	 */
	public static final Color NOTE_INDEX_COLORS[] = {
		Color.red,
		new Color(0x40,0x40,0xFF),
		Color.orange.darker(),
		new Color(0x20,0x99,0x00),
		Color.magenta,
		Color.orange,
		Color.green
	};
	/**
	 * ルート音の半音差（ないのと同じ）
	 */
	public static final int ROOT = 0;
	/**
	 * 長２度の半音差
	 */
	public static final int SUS2 = 2;
	/**
	 * 短３度または増２度の半音差
	 */
	public static final int MINOR = 3;
	/**
	 * 長３度の半音差
	 */
	public static final int MAJOR = 4;
	/**
	 * 完全４度の半音差
	 */
	public static final int SUS4 = 5;	//
	/**
	 * 減５度または増４度の半音差（トライトーン ＝ 三全音 ＝ 半オクターブ）
	 */
	public static final int FLAT5 = 6;
	/**
	 * 完全５度の半音差
	 */
	public static final int PARFECT5 = 7;
	/**
	 * 増５度または短６度の半音差
	 */
	public static final int SHARP5 = 8;
	/**
	 * 長６度または減７度の半音差
	 */
	public static final int SIXTH = 9;
	/**
	 * 短７度の半音差
	 */
	public static final int SEVENTH = 10;
	/**
	 * 長７度の半音差
	 */
	public static final int MAJOR_SEVENTH = 11;
	/**
	 * 短９度（短２度の１オクターブ上）の半音差
	 */
	public static final int FLAT9 = 13;
	/**
	 * 長９度（長２度の１オクターブ上）の半音差
	 */
	public static final int NINTH = 14;
	/**
	 * 増９度（増２度の１オクターブ上）の半音差
	 */
	public static final int SHARP9 = 15;
	/**
	 * 完全１１度（完全４度の１オクターブ上）の半音差
	 */
	public static final int ELEVENTH = 17;
	/**
	 * 増１１度（増４度の１オクターブ上）の半音差
	 */
	public static final int SHARP11 = 18;
	/**
	 * 短１３度（短６度の１オクターブ上）の半音差
	 */
	public static final int FLAT13 = 20;
	/**
	 * 長１３度（長６度の１オクターブ上）の半音差
	 */
	public static final int THIRTEENTH = 21;
	//
	// index
	public static final int THIRD_OFFSET	= 0;	// ３度
	public static final int FIFTH_OFFSET	= 1;	// ５度
	public static final int SEVENTH_OFFSET	= 2;	// ７度
	public static final int NINTH_OFFSET	= 3;	// ９度
	public static final int ELEVENTH_OFFSET	= 4;	// １１度
	public static final int THIRTEENTH_OFFSET	= 5;	// １３度
	/**
	 * デフォルトの半音値（メジャーコード固定）
	 */
	public static int DEFAULT_OFFSETS[] = {
		MAJOR, PARFECT5, ROOT, ROOT, ROOT, ROOT,
	};
	/**
	 * このコードのルート音
	 */
	private NoteSymbol rootNoteSymbol;
	/**
	 * このコードのベース音（ルート音と異なる場合は分数コードの分母）
	 */
	private NoteSymbol bassNoteSymbol;
	/**
	 * 現在の半音値
	 */
	public int offsets[] =
		Arrays.copyOf(DEFAULT_OFFSETS, DEFAULT_OFFSETS.length);
	/**
	 * コード C major を構築します。
	 */
	public Chord() {
		this(new NoteSymbol());
	}
	/**
	 * 指定した音名のメジャーコードを構築します。
	 * @param noteSymbol 音名
	 */
	public Chord(NoteSymbol noteSymbol) {
		setRoot(noteSymbol);
		setBass(noteSymbol);
	}
	/**
	 * 指定された調と同名のコードを構築します。
	 * <p>元の調がマイナーキーの場合はマイナーコード、
	 * それ以外の場合はメジャーコードになります。
	 * </p>
	 * @param key 調
	 */
	public Chord(Key key) {
		int keyCo5 = key.toCo5();
		if( key.majorMinor() == MINOR ) {
			keyCo5 += 3;
			setMinorThird();
		}
		setRoot(new NoteSymbol(keyCo5));
		setBass(new NoteSymbol(keyCo5));
	}
	/**
	 * コード名の文字列からコードを構築します。
	 * @param chordSymbol コード名の文字列
	 */
	public Chord(String chordSymbol) {
		setChordSymbol(chordSymbol);
	}
	/**
	 * このコードのクローンを作成します。
	 */
	@Override
	public Chord clone() {
		Chord newChord = new Chord(rootNoteSymbol);
		newChord.offsets = Arrays.copyOf( offsets, offsets.length );
		newChord.setBass(bassNoteSymbol);
		return newChord;
	}
	/**
	 * コードのルート音を指定された音階に置換します。
	 * @param rootNoteSymbol 音階
	 * @return このコード自身（置換後）
	 */
	public Chord setRoot(NoteSymbol rootNoteSymbol) {
		this.rootNoteSymbol = rootNoteSymbol;
		return this;
	}
	/**
	 * コードのベース音を指定された音階に置換します。
	 * @param rootNoteSymbol 音階
	 * @return このコード自身（置換後）
	 */
	public Chord setBass(NoteSymbol rootNoteSymbol) {
		this.bassNoteSymbol = rootNoteSymbol;
		return this;
	}
	// コードの種類を設定します。
	//
	public void setMajorThird() { offsets[THIRD_OFFSET] = MAJOR; }
	public void setMinorThird() { offsets[THIRD_OFFSET] = MINOR; }
	public void setSus4() { offsets[THIRD_OFFSET] = SUS4; }
	public void setSus2() { offsets[THIRD_OFFSET] = SUS2; }
	//
	public void setParfectFifth() { offsets[FIFTH_OFFSET] = PARFECT5; }
	public void setFlattedFifth() { offsets[FIFTH_OFFSET] = FLAT5; }
	public void setSharpedFifth() { offsets[FIFTH_OFFSET] = SHARP5; }
	//
	public void clearSeventh() { offsets[SEVENTH_OFFSET] = ROOT; }
	public void setMajorSeventh() { offsets[SEVENTH_OFFSET] = MAJOR_SEVENTH; }
	public void setSeventh() { offsets[SEVENTH_OFFSET] = SEVENTH; }
	public void setSixth() { offsets[SEVENTH_OFFSET] = SIXTH; }
	//
	public void clearNinth() { offsets[NINTH_OFFSET] = ROOT; }
	public void setNinth() { offsets[NINTH_OFFSET] = NINTH; }
	public void setSharpedNinth() { offsets[NINTH_OFFSET] = SHARP9; }
	public void setFlattedNinth() { offsets[NINTH_OFFSET] = FLAT9; }
	//
	public void clearEleventh() { offsets[ELEVENTH_OFFSET] = ROOT; }
	public void setEleventh() { offsets[ELEVENTH_OFFSET] = ELEVENTH; }
	public void setSharpedEleventh() { offsets[ELEVENTH_OFFSET] = SHARP11; }
	//
	public void clearThirteenth() { offsets[THIRTEENTH_OFFSET] = ROOT; }
	public void setThirteenth() { offsets[THIRTEENTH_OFFSET] = THIRTEENTH; }
	public void setFlattedThirteenth() { offsets[THIRTEENTH_OFFSET] = FLAT13; }
	//
	// コードネームの文字列が示すコードに置き換えます。
	public Chord setChordSymbol(String chord_symbol) {
		//
		// 分数コードの分子と分母に分ける
		String parts[] = chord_symbol.trim().split("(/|on)");
		if( parts.length == 0 ) {
			return this;
		}
		// ルート音とベース音を設定
		setRoot(new NoteSymbol(parts[0]));
		setBass(new NoteSymbol(parts[ parts.length > 1 ? 1 : 0 ]));
		String suffix = parts[0].replaceFirst("^[A-G][#bx]*","");
		//
		// () があれば、その中身を取り出す
		String suffixParts[] = suffix.split("[\\(\\)]");
		if( suffixParts.length == 0 ) {
			return this;
		}
		String suffixParen = "";
		if( suffixParts.length > 1 ) {
			suffixParen = suffixParts[1];
			suffix = suffixParts[0];
		}
		//
		// +5 -5 aug dim の判定
		offsets[FIFTH_OFFSET] = (
			suffix.matches( ".*(\\+5|aug|#5).*" ) ? SHARP5 :
			suffix.matches( ".*(-5|dim|b5).*" ) ? FLAT5 :
			PARFECT5
		);
		// 6 7 M7 の判定
		offsets[SEVENTH_OFFSET] = (
			suffix.matches( ".*(M7|maj7|M9|maj9).*" ) ? MAJOR_SEVENTH :
			suffix.matches( ".*(6|dim[79]).*" ) ? SIXTH :
			suffix.matches( ".*7.*" ) ? SEVENTH :
			ROOT
		);
		// マイナーの判定。maj7 と間違えないように比較
		offsets[THIRD_OFFSET] = (
			(suffix.matches( ".*m.*" ) && ! suffix.matches(".*ma.*") ) ? MINOR :
			suffix.matches( ".*sus4.*" ) ? SUS4 :
			MAJOR
		);
		// 9th の判定
		if( suffix.matches( ".*9.*" ) ) {
			offsets[NINTH_OFFSET] = NINTH;
			if( ! suffix.matches( ".*(add9|6|M9|maj9|dim9).*") ) {
				offsets[SEVENTH_OFFSET] = SEVENTH;
			}
		}
		else {
			offsets[NINTH_OFFSET] =
			offsets[ELEVENTH_OFFSET] =
			offsets[THIRTEENTH_OFFSET] = ROOT;
			//
			// () の中を , で分ける
			String parts_in_paren[] = suffixParen.split(",");
			for( String p : parts_in_paren ) {
				if( p.matches("(\\+9|#9)") ) offsets[NINTH_OFFSET] = SHARP9;
				else if( p.matches("(-9|b9)") ) offsets[NINTH_OFFSET] = FLAT9;
				else if( p.matches("9") ) offsets[NINTH_OFFSET] = NINTH;

				if( p.matches("(\\+11|#11)") ) offsets[ELEVENTH_OFFSET] = SHARP11;
				else if( p.matches("11") ) offsets[ELEVENTH_OFFSET] = ELEVENTH;

				if( p.matches("(-13|b13)") ) offsets[THIRTEENTH_OFFSET] = FLAT13;
				else if( p.matches("13") ) offsets[THIRTEENTH_OFFSET] = THIRTEENTH;

				// -5 や +5 が () の中にあっても解釈できるようにする
				if( p.matches("(-5|b5)") ) offsets[FIFTH_OFFSET] = FLAT5;
				else if( p.matches("(\\+5|#5)") ) offsets[FIFTH_OFFSET] = SHARP5;
			}
		}
		return this;
	}
	/**
	 * ルート音を返します。
	 * @return ルート音
	 */
	public NoteSymbol rootNoteSymbol() { return rootNoteSymbol; }
	/**
	 * ベース音を返します。分数コードの場合はルート音と異なります。
	 * @return ベース音
	 */
	public NoteSymbol bassNoteSymbol() { return bassNoteSymbol; }
	//
	// コードの種類を調べます。
	public boolean isMajor() { return offsets[THIRD_OFFSET] == MAJOR; }
	public boolean isMinor() { return offsets[THIRD_OFFSET] == MINOR; }
	public boolean isSus4() { return offsets[THIRD_OFFSET] == SUS4; }
	public boolean isSus2() { return offsets[THIRD_OFFSET] == SUS2; }
	//
	public boolean hasParfectFifth() { return offsets[FIFTH_OFFSET] == PARFECT5; }
	public boolean hasFlattedFifth() { return offsets[FIFTH_OFFSET] == FLAT5; }
	public boolean hasSharpedFifth() { return offsets[FIFTH_OFFSET] == SHARP5; }
	//
	public boolean hasNoSeventh() { return offsets[SEVENTH_OFFSET] == ROOT; }
	public boolean hasSeventh() { return offsets[SEVENTH_OFFSET] == SEVENTH; }
	public boolean hasMajorSeventh() { return offsets[SEVENTH_OFFSET] == MAJOR_SEVENTH; }
	public boolean hasSixth() { return offsets[SEVENTH_OFFSET] == SIXTH; }
	//
	public boolean hasNoNinth() { return offsets[NINTH_OFFSET] == ROOT; }
	public boolean hasNinth() { return offsets[NINTH_OFFSET] == NINTH; }
	public boolean hasFlattedNinth() { return offsets[NINTH_OFFSET] == FLAT9; }
	public boolean hasSharpedNinth() { return offsets[NINTH_OFFSET] == SHARP9; }
	//
	public boolean hasNoEleventh() { return offsets[ELEVENTH_OFFSET] == ROOT; }
	public boolean hasEleventh() { return offsets[ELEVENTH_OFFSET] == ELEVENTH; }
	public boolean hasSharpedEleventh() { return offsets[ELEVENTH_OFFSET] == SHARP11; }
	//
	public boolean hasNoThirteenth() { return offsets[THIRTEENTH_OFFSET] == ROOT; }
	public boolean hasThirteenth() { return offsets[THIRTEENTH_OFFSET] == THIRTEENTH; }
	public boolean hasFlattedThirteenth() { return offsets[THIRTEENTH_OFFSET] == FLAT13; }
	/**
	 * コードが等しいかどうかを判定します。
	 * @return 等しければtrue
	 */
	@Override
	public boolean equals(Object anObject) {
		if( this == anObject )
			return true;
		if( anObject instanceof Chord ) {
			Chord another = (Chord) anObject;
			if( ! rootNoteSymbol.equals(another.rootNoteSymbol) )
				return false;
			if( ! bassNoteSymbol.equals(another.bassNoteSymbol) )
				return false;
			return Arrays.equals(offsets, another.offsets);
		}
		return false;
	}
	@Override
	public int hashCode() {
		return toString().hashCode();
	}
	/**
	 * コードが等しいかどうかを、異名同音を無視して判定します。
	 * @param another 比較対象のコード
	 * @return 等しければtrue
	 */
	public boolean equalsEnharmonically(Chord another) {
		if( this == another )
			return true;
		if( another == null )
			return false;
		if( ! rootNoteSymbol.equalsEnharmonically(another.rootNoteSymbol) )
			return false;
		if( ! bassNoteSymbol.equalsEnharmonically(another.bassNoteSymbol) )
			return false;
		return Arrays.equals(offsets, another.offsets);
	}
	/**
	 * コード構成音の数を返します（ベース音は含まれません）。
	 * @return コード構成音の数
	 */
	public int numberOfNotes() {
		int n=1;
		for( int offset : offsets ) if( offset != ROOT ) n++;
		return n;
	}
	/**
	 * 指定された位置にあるノート番号を返します。
	 * @param index 位置（０をルート音とした構成音の順序）
	 * @return ノート番号（該当する音がない場合は -1）
	 */
	public int noteAt(int index) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		if( index == 0 )
			return rootnote;
		int i=0;
		for( int offset : offsets )
			if( offset != ROOT && ++i == index )
				return rootnote + offset;
		return -1;
	}
	// コード構成音を格納したノート番号の配列を返します。
	//（ベース音は含まれません）
	// 音域が指定された場合、その音域に合わせたノート番号を返します。
	//
	public int[] toNoteArray() {
		return toNoteArray( (Range)null, (Key)null );
	}
	public int[] toNoteArray(Range range) {
		return toNoteArray( range, (Key)null );
	}
	public int[] toNoteArray(Range range, Key key) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		int ia[] = new int[numberOfNotes()];
		int i;
		ia[i=0] = rootnote;
		for( int offset : offsets )
			if( offset != ROOT )
				ia[++i] = rootnote + offset;
		if( range != null ) range.invertNotesOf( ia, key );
		return ia;
	}
	/**
	 * MIDI ノート番号が、コードの構成音の何番目（０＝ルート音）に
	 * あるかを表すインデックス値を返します。
	 * 構成音に該当しない場合は -1 を返します。
	 * ベース音は検索されません。
	 * @param noteNumber MIDIノート番号
	 * @return 構成音のインデックス値
	 */
	public int indexOf(int noteNumber) {
		int relative_note = noteNumber - rootNoteSymbol.toNoteNumber();
		if( Music.mod12(relative_note) == 0 ) return 0;
		int i=0;
		for( int offset : offsets ) if( offset != ROOT ) {
			i++;
			if( Music.mod12(relative_note - offset) == 0 )
				return i;
		}
		return -1;
	}
	// 構成音がそのキーのスケールを外れていないか調べます。
	public boolean isOnScaleInKey( Key key ) {
		return isOnScaleInKey( key.toCo5() );
	}
	public boolean isOnScaleInKey( int key_co5 ) {
		int rootnote = rootNoteSymbol.toNoteNumber();
		if( ! Music.isOnScale( rootnote, key_co5 ) )
			return false;
		for( int offset : offsets )
			if( offset != ROOT && ! Music.isOnScale( rootnote + offset, key_co5 ) )
				return false;
		return true;
	}
	//
	// コードを移調します。
	// 移調幅は chromatic_offset で半音単位に指定します。
	// 移調幅が０の場合、自分自身をそのまま返します。
	//
	public Chord transpose(int chromatic_offset) {
		return transpose(chromatic_offset, 0);
	}
	public Chord transpose(int chromatic_offset, Key original_key) {
		return transpose(chromatic_offset, original_key.toCo5());
	}
	public Chord transpose(int chromatic_offset, int original_key_co5) {
		if( chromatic_offset == 0 ) return this;
		int offsetCo5 = Music.mod12(Music.reverseCo5(chromatic_offset));
		if( offsetCo5 > 6 ) offsetCo5 -= 12;
		int key_co5   = original_key_co5 + offsetCo5;
		//
		int newRootCo5 = rootNoteSymbol.toCo5() + offsetCo5;
		int newBassCo5 = bassNoteSymbol.toCo5() + offsetCo5;
		if( key_co5 > 6 ) {
			newRootCo5 -= 12;
			newBassCo5 -= 12;
		}
		else if( key_co5 < -5 ) {
			newRootCo5 += 12;
			newBassCo5 += 12;
		}
		setRoot(new NoteSymbol(newRootCo5));
		return setBass(new NoteSymbol(newBassCo5));
	}
	/**
	 * この和音の文字列表現としてコード名を返します。
	 * @return この和音のコード名
	 */
	public String toString() {
		String chordSymbol = rootNoteSymbol + symbolSuffix();
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			chordSymbol += "/" + bassNoteSymbol;
		}
		return chordSymbol;
	}
	/**
	 * コード名を HTML で返します。
	 *
	 * Swing の {@link JLabel#setText(String)} は HTML で指定できるので、
	 * 文字の大きさに変化をつけることができます。
	 *
	 * @param color_name 色のHTML表現（色名または #RRGGBB 形式）
	 * @return コード名のHTML
	 */
	public String toHtmlString(String color_name) {
		String small_tag = "<span style=\"font-size: 120%\">";
		String end_of_small_tag = "</span>";
		String root = rootNoteSymbol.toString();
		String formatted_root = (root.length() == 1) ? root + small_tag :
			root.replace("#",small_tag+"<sup>#</sup>").
			replace("b",small_tag+"<sup>b</sup>").
			replace("x",small_tag+"<sup>x</sup>");
		String formatted_bass = "";
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			String bass = bassNoteSymbol.toString();
			formatted_bass = (bass.length() == 1) ? bass + small_tag :
				bass.replace("#",small_tag+"<sup>#</sup>").
				replace("b",small_tag+"<sup>b</sup>").
				replace("x",small_tag+"<sup>x</sup>");
			formatted_bass = "/" + formatted_bass + end_of_small_tag;
		}
		String suffix = symbolSuffix().
			replace("-5","<sup>-5</sup>").
			replace("+5","<sup>+5</sup>");
		return
			"<html>" +
			"<span style=\"color: " + color_name + "; font-size: 170% ; white-space: nowrap ;\">" +
			formatted_root + suffix + end_of_small_tag + formatted_bass +
			"</span>" +
			"</html>" ;
	}
	/**
	 * コードの説明（英語）を返します。
	 * @return コードの説明（英語）
	 */
	public String toName() {
		String chord_name = rootNoteSymbol.toStringIn(SymbolLanguage.NAME) + nameSuffix() ;
		if( ! rootNoteSymbol.equals(bassNoteSymbol) ) {
			chord_name += " on " + bassNoteSymbol.toStringIn(SymbolLanguage.NAME);
		}
		return chord_name;
	}
	/**
	 * コードネームの音名を除いた部分（サフィックス）を組み立てて返します。
	 * @return コードネームの音名を除いた部分
	 */
	public String symbolSuffix() {
		String suffix = ( ( offsets[THIRD_OFFSET] == MINOR ) ? "m" : "" );
		switch( offsets[SEVENTH_OFFSET] ) {
		case SIXTH:         suffix += "6";  break;
		case SEVENTH:       suffix += "7";  break;
		case MAJOR_SEVENTH: suffix += "M7"; break;
		}
		switch( offsets[THIRD_OFFSET] ) {
		case SUS4: suffix += "sus4"; break;
		case SUS2: suffix += "sus2"; break;
		default: break;
		}
		switch( offsets[FIFTH_OFFSET] ) {
		case FLAT5:  suffix += "-5"; break;
		case SHARP5: suffix += "+5"; break;
		default: break;
		}
		Vector<String> paren = new Vector<String>();
		switch( offsets[NINTH_OFFSET] ) {
		case NINTH:  paren.add("9"); break;
		case FLAT9:  paren.add("-9"); break;
		case SHARP9: paren.add("+9"); break;
		}
		switch( offsets[ELEVENTH_OFFSET] ) {
		case ELEVENTH: paren.add("11"); break;
		case SHARP11:  paren.add("+11"); break;
		}
		switch( offsets[THIRTEENTH_OFFSET] ) {
		case THIRTEENTH: paren.add("13"); break;
		case FLAT13:     paren.add("-13"); break;
		}
		if( paren.size() > 0 ) {
			boolean is_first = true;
			suffix += "(";
			for( String p : paren ) {
				if( is_first )
					is_first = false;
				else
					suffix += ",";
				suffix += p;
			}
			suffix += ")";
		}
		if( suffix.equals("m-5") ) return "dim";
		else if( suffix.equals("+5") ) return "aug";
		else if( suffix.equals("m6-5") ) return "dim7";
		else if( suffix.equals("(9)") ) return "add9";
		else if( suffix.equals("7(9)") ) return "9";
		else if( suffix.equals("M7(9)") ) return "M9";
		else if( suffix.equals("7+5") ) return "aug7";
		else if( suffix.equals("m6-5(9)") ) return "dim9";
		else return suffix ;
	}
	/**
	 * コードの説明のうち、音名を除いた部分を組み立てて返します。
	 * @return コード説明の音名を除いた部分
	 */
	public String nameSuffix() {
		String suffix = "";
		if( offsets[THIRD_OFFSET] == MINOR ) suffix += " minor";
		switch( offsets[SEVENTH_OFFSET] ) {
		case SIXTH:         suffix += " 6th"; break;
		case SEVENTH:       suffix += " 7th"; break;
		case MAJOR_SEVENTH: suffix += " major 7th"; break;
		}
		switch( offsets[THIRD_OFFSET] ) {
		case SUS4: suffix += " suspended 4th"; break;
		case SUS2: suffix += " suspended 2nd"; break;
		default: break;
		}
		switch( offsets[FIFTH_OFFSET] ) {
		case FLAT5 : suffix += " flatted 5th"; break;
		case SHARP5: suffix += " sharped 5th"; break;
		default: break;
		}
		Vector<String> paren = new Vector<String>();
		switch( offsets[NINTH_OFFSET] ) {
		case NINTH:  paren.add("9th"); break;
		case FLAT9:  paren.add("flatted 9th"); break;
		case SHARP9: paren.add("sharped 9th"); break;
		}
		switch( offsets[ELEVENTH_OFFSET] ) {
		case ELEVENTH: paren.add("11th"); break;
		case SHARP11:  paren.add("sharped 11th"); break;
		}
		switch( offsets[THIRTEENTH_OFFSET] ) {
		case THIRTEENTH: paren.add("13th"); break;
		case FLAT13:     paren.add("flatted 13th"); break;
		}
		if( paren.size() > 0 ) {
			boolean is_first = true;
			suffix += "(additional ";
			for( String p : paren ) {
				if( is_first )
					is_first = false;
				else
					suffix += ",";
				suffix += p;
			}
			suffix += ")";
		}
		if( suffix.equals(" minor flatted 5th") ) return " diminished (triad)";
		else if( suffix.equals(" sharped 5th") ) return " augumented";
		else if( suffix.equals(" minor 6th flatted 5th") ) return " diminished 7th";
		else if( suffix.equals(" 7th(additional 9th)") ) return " 9th";
		else if( suffix.equals(" major 7th(additional 9th)") ) return " major 9th";
		else if( suffix.equals(" 7th sharped 5th") ) return " augumented 7th";
		else if( suffix.equals(" minor 6th flatted 5th(additional 9th)") ) return " diminished 9th";
		else if( suffix.isEmpty() ) return " major";
		else return suffix ;
	}
}