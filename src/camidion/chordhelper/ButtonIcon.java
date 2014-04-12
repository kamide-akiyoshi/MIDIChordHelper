package camidion.chordhelper;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.AbstractButton;
import javax.swing.Icon;

import camidion.chordhelper.music.Chord;

/**
 * カスタムペイントアイコン
 */
public class ButtonIcon implements Icon {
	public static final int BLANK_ICON = 0;
	public static final int REC_ICON = 1;
	public static final int PLAY_ICON = 2;
	public static final int STOP_ICON = 3;
	public static final int EJECT_ICON = 4;
	public static final int PAUSE_ICON = 5;
	public static final int ANO_GAKKI_ICON = 6;
	//
	public static final int INVERSION_ICON = 8;
	public static final int DARK_MODE_ICON = 9;
	public static final int X_ICON = 10;
	public static final int REPEAT_ICON = 11;
	public static final int MIDI_CONNECTOR_ICON = 12;
	public static final int NATURAL_ICON = 13;
	public static final int EDIT_ICON = 14;
	public static final int FORWARD_ICON = 15;
	public static final int BACKWARD_ICON = 16;
	public static final int TOP_ICON = 17;
	public static final int BOTTOM_ICON = 18;
	//
	public static final int A128TH_NOTE_ICON = 128;
	public static final int DOTTED_128TH_NOTE_ICON = 129;
	public static final int A64TH_NOTE_ICON = 130;
	public static final int DOTTED_64TH_NOTE_ICON = 131;
	public static final int A32ND_NOTE_ICON = 132;
	public static final int DOTTED_32ND_NOTE_ICON = 133;
	public static final int A16TH_NOTE_ICON = 134;
	public static final int DOTTED_16TH_NOTE_ICON = 135;
	public static final int A8TH_NOTE_ICON = 136;
	public static final int DOTTED_8TH_NOTE_ICON = 137;
	public static final int QUARTER_NOTE_ICON = 138;
	public static final int DOTTED_QUARTER_NOTE_ICON = 139;
	public static final int HALF_NOTE_ICON = 140;
	public static final int DOTTED_HALF_NOTE_ICON = 141;
	public static final int WHOLE_NOTE_ICON = 142;
	//
	private int iconKind;
	public int getIconKind() { return iconKind; }
	//
	public boolean isMusicalNote() {
		return iconKind >= A128TH_NOTE_ICON && iconKind <= WHOLE_NOTE_ICON ;
	}
	public boolean isDottedMusicalNote() {
		return isMusicalNote() && ((iconKind & 1) != 0) ;
	}
	public int getMusicalNoteValueIndex() { // Returns log2(n) of n-th note
		return isMusicalNote() ? (WHOLE_NOTE_ICON + 1 - iconKind) / 2 : -1 ;
	}
	//
	private int width = 16;
	private static final int HEIGHT = 16;
	private static final int MARGIN = 3;
	//
	// for notes
	private static final int NOTE_HEAD_WIDTH = 8;
	private static final int NOTE_HEAD_HEIGHT = 6;
	//
	// for eject button
	private static final int EJECT_BOTTOM_LINE_WIDTH = 2;
	//
	// for play/eject button
	private int xPoints[];
	private int yPoints[];
	//
	public ButtonIcon(int kind) {
		iconKind = kind;
		switch( iconKind ) {
		case PLAY_ICON:
			xPoints = new int[4]; yPoints = new int[4];
			xPoints[0] = MARGIN;       yPoints[0] = MARGIN;
			xPoints[1] = width-MARGIN; yPoints[1] = HEIGHT/2;
			xPoints[2] = MARGIN;       yPoints[2] = HEIGHT-MARGIN;
			xPoints[3] = MARGIN;       yPoints[3] = MARGIN;
			break;
		case EJECT_ICON:
			xPoints = new int[4]; yPoints = new int[4];
			xPoints[0] = width/2;      yPoints[0] = MARGIN;
			xPoints[1] = width-MARGIN; yPoints[1] = HEIGHT - MARGIN - 2*EJECT_BOTTOM_LINE_WIDTH;
			xPoints[2] = MARGIN;       yPoints[2] = HEIGHT - MARGIN - 2*EJECT_BOTTOM_LINE_WIDTH;
			xPoints[3] = width/2;      yPoints[3] = MARGIN;
			break;
		case TOP_ICON:
		case BACKWARD_ICON:
			xPoints = new int[8]; yPoints = new int[8];
			xPoints[0] = width-MARGIN; yPoints[0] = MARGIN;
			xPoints[1] = width-MARGIN; yPoints[1] = HEIGHT-MARGIN;
			xPoints[2] = width/2;      yPoints[2] = HEIGHT/2;
			xPoints[3] = width/2;      yPoints[3] = HEIGHT-MARGIN;
			xPoints[4] = MARGIN;       yPoints[4] = HEIGHT/2;
			xPoints[5] = width/2;      yPoints[5] = MARGIN;
			xPoints[6] = width/2;      yPoints[6] = HEIGHT/2;
			xPoints[7] = width-MARGIN; yPoints[7] = MARGIN;
			break;
		case BOTTOM_ICON:
		case FORWARD_ICON:
			xPoints = new int[8]; yPoints = new int[8];
			xPoints[0] = MARGIN;       yPoints[0] = MARGIN;
			xPoints[1] = MARGIN;       yPoints[1] = HEIGHT-MARGIN;
			xPoints[2] = width/2;      yPoints[2] = HEIGHT/2;
			xPoints[3] = width/2;      yPoints[3] = HEIGHT-MARGIN;
			xPoints[4] = width-MARGIN;       yPoints[4] = HEIGHT/2;
			xPoints[5] = width/2;      yPoints[5] = MARGIN;
			xPoints[6] = width/2;      yPoints[6] = HEIGHT/2;
			xPoints[7] = MARGIN;       yPoints[7] = MARGIN;
			break;
		case INVERSION_ICON:
		case ANO_GAKKI_ICON:
			width = 32;
			break;
		case REPEAT_ICON:
			xPoints = new int[4]; yPoints = new int[4];
			xPoints[0] = width/2 - 2;  yPoints[0] = MARGIN;
			xPoints[1] = width/2 + 2;  yPoints[1] = MARGIN - 4;
			xPoints[2] = width/2 + 2;  yPoints[2] = MARGIN + 5;
			xPoints[3] = width/2 - 2;  yPoints[3] = MARGIN + 1;
			break;
		}
	}
	@Override
	public int getIconWidth() { return width; }
	@Override
	public int getIconHeight() { return HEIGHT; }
	@Override
	public void paintIcon(Component c, Graphics g, int x, int y) {
		Graphics2D g2 = (Graphics2D) g;
		boolean is_selected = (
			(
				(c instanceof AbstractButton) && ((AbstractButton)c).isSelected()
				)||(
				(c instanceof InversionAndOmissionLabel) && (
					((InversionAndOmissionLabel)c).isAutoInversionMode()
				)
			)
		);
		int omitting_note = c instanceof InversionAndOmissionLabel ?
			((InversionAndOmissionLabel)c).getOmissionNoteIndex() : -1;
		g2.setColor( c.isEnabled() ? c.getForeground() : c.getBackground().darker() );
		g2.translate(x, y);
		switch(iconKind) {
		case REC_ICON:
			if( c.isEnabled() ) g.setColor(Color.red);
			g2.fillOval( MARGIN, MARGIN, width - 2*MARGIN, HEIGHT - 2*MARGIN );
			break;
		case TOP_ICON:
			g2.fillRect( MARGIN-1, MARGIN, 2, HEIGHT - 2*MARGIN );
			// No break;
		case BACKWARD_ICON:
		case FORWARD_ICON:
		case PLAY_ICON:
			g2.fillPolygon( xPoints, yPoints, xPoints.length );
			break;
		case BOTTOM_ICON:
			g2.fillRect( width-1-MARGIN, MARGIN, 2, HEIGHT - 2*MARGIN );
			g2.fillPolygon( xPoints, yPoints, xPoints.length );
			break;
		case STOP_ICON:
			g2.fillRect( MARGIN+1, MARGIN+1, width - 2*(MARGIN+1), HEIGHT - 2*(MARGIN+1) );
			break;
		case PAUSE_ICON:
			g2.fillRect( MARGIN+1, MARGIN+1, width/5, HEIGHT - 2*(MARGIN+1) );
			g2.fillRect( width-1-MARGIN-width/5, MARGIN+1, width/5, HEIGHT - 2*(MARGIN+1) );
			break;
		case EJECT_ICON:
			g2.fillPolygon( xPoints, yPoints, xPoints.length );
			g2.fillRect(
				MARGIN+1,
				HEIGHT - MARGIN - EJECT_BOTTOM_LINE_WIDTH,
				width - 2*MARGIN - 1,
				EJECT_BOTTOM_LINE_WIDTH
			);
			break;

		case ANO_GAKKI_ICON:
			g2.setBackground( c.getBackground() );
			g2.clearRect( 0,  0, width, HEIGHT );
			g2.setColor(Color.cyan);
			g2.drawRect(  4, 4, 10, 10 );
			g2.drawLine(  1, 14, 30, 4 );
			g2.drawOval(  18, 1, 12, 12 );
			if( ! is_selected ) {
				// g2.setStroke(new BasicStroke(2));
				g2.setColor(Color.red);
				g2.drawLine( 0, 0, width-1, HEIGHT-1 );
				g2.drawLine( 0, HEIGHT-1, width-1, 0 );
			}
			break;

		case INVERSION_ICON:
			g2.setBackground( c.getBackground() );
			g2.clearRect( 0,  0, width, HEIGHT );
			g2.setColor( c.getBackground().darker() );
			g2.drawRect(  0,  0, width-1, HEIGHT-1 );
			g2.drawLine(  8,  0,  8, HEIGHT );
			g2.drawLine( 16,  0, 16, HEIGHT );
			g2.drawLine( 24,  0, 24, HEIGHT );
			g2.setColor( c.getForeground() );
			g2.fillRect(  6,  0,  5,  HEIGHT/2 );
			g2.fillRect( 14,  0,  5,  HEIGHT/2 );
			g2.fillRect( 22,  0,  5,  HEIGHT/2 );
			if( is_selected ) {
				g2.setColor( Chord.NOTE_INDEX_COLORS[1] );
				g2.fillOval( 2, 10, 4, 4 );
				if( omitting_note == 1 ) {
					g2.setColor( c.getForeground() );
					g2.drawLine( 1, 9, 7, 15 );
					g2.drawLine( 1, 15, 7, 9 );
				}
				g2.setColor( Chord.NOTE_INDEX_COLORS[2] );
				g2.fillOval( 10, 10, 4, 4 );
				if( omitting_note == 2 ) {
					g2.setColor( c.getForeground() );
					g2.drawLine( 9, 9, 15, 15 );
					g2.drawLine( 9, 15, 15, 9 );
				}
				g2.setColor( Chord.NOTE_INDEX_COLORS[0] );
				g2.fillOval( 26, 10, 4, 4 );
				if( omitting_note == 0 ) {
					g2.setColor( c.getForeground() );
					g2.drawLine( 25, 9, 31, 15 );
					g2.drawLine( 25, 15, 31, 9 );
				}
			}
			else {
				g2.setColor( Chord.NOTE_INDEX_COLORS[0] );
				g2.fillOval( 1, 9, 6, 6 );
				if( omitting_note == 0 ) {
					g2.setColor( c.getForeground() );
					g2.drawLine( 1, 9, 7, 15 );
					g2.drawLine( 1, 15, 7, 9 );
				}
				g2.setColor( Chord.NOTE_INDEX_COLORS[1] );
				g2.fillOval( 10, 10, 4, 4 );
				if( omitting_note == 1 ) {
					g2.setColor( c.getForeground() );
					g2.drawLine( 9, 9, 15, 15 );
					g2.drawLine( 9, 15, 15, 9 );
				}
				g2.setColor( Chord.NOTE_INDEX_COLORS[2] );
				g2.fillOval( 18, 10, 4, 4 );
				if( omitting_note == 2 ) {
					g2.setColor( c.getForeground() );
					g2.drawLine( 17, 9, 23, 15 );
					g2.drawLine( 17, 15, 23, 9 );
				}
			}
			break;
		case DARK_MODE_ICON:
			if( is_selected ) {
				g2.setColor( c.getForeground().darker() );
				g2.fillRect( 0, 0, width, HEIGHT );
				g2.setColor( Color.gray );
				g2.fillRect( width-2, 0, 2, HEIGHT );
				g2.drawLine( 0, 0, width-1, 0 );
				g2.setColor( Color.gray.darker() );
				g2.drawLine( 0, 0, 0, HEIGHT-1 );
				g2.drawLine( 0, HEIGHT-1, width-1, HEIGHT-1 );
				g2.setColor( Color.orange.brighter() );
				g2.fillRect( width-6, HEIGHT/2-3, 2, 6 );
			}
			else {
				g2.setColor( c.getBackground().brighter() );
				g2.fillRect( 0, 0, width, HEIGHT );
				g2.setColor( Color.gray.brighter() );
				g2.drawLine( 0, 0, width-1, 0 );
				g2.drawLine( width-1, 0, width-1, HEIGHT-1 );
				g2.setColor( Color.gray );
				g2.fillRect( 0, 0, 2, HEIGHT );
				g2.drawLine( 0, HEIGHT-1, width-1, HEIGHT-1 );
				g2.setColor( Color.gray.brighter() );
				g2.fillRect( width-6, HEIGHT/2-4, 4, 7 );
				g2.setColor( c.getForeground() );
				g2.fillRect( width-5, HEIGHT/2-3, 2, 5 );
			}
			break;
		case X_ICON:
			g2.drawLine( 4, 5, width-5, HEIGHT-4 );
			g2.drawLine( 4, 4, width-4, HEIGHT-4 );
			g2.drawLine( 5, 4, width-4, HEIGHT-5 );
			g2.drawLine( width-5, 4, 4, HEIGHT-5 );
			g2.drawLine( width-4, 4, 4, HEIGHT-4 );
			g2.drawLine( width-4, 5, 5, HEIGHT-4 );
			break;
		case REPEAT_ICON:
			g2.drawArc( MARGIN, MARGIN, width - 2*MARGIN, HEIGHT - 2*MARGIN, 150, 300 );
			g2.fillPolygon( xPoints, yPoints, xPoints.length );
			break;
		case MIDI_CONNECTOR_ICON:
			g2.drawOval( 0, 0, width - 2, HEIGHT - 2 );
			g2.fillRect( width/2-2, HEIGHT-4, 3, 3 );
			g2.fillOval( width/2-2, 2, 3, 3 );
			g2.fillOval( width/2-5, 4, 2, 2 );
			g2.fillOval( width/2+2, 4, 2, 2 );
			g2.fillOval( width/2-6, 7, 2, 2 );
			g2.fillOval( width/2+3, 7, 2, 2 );
			break;
		case NATURAL_ICON:
			g2.drawLine( width/2-2, 1, width/2-2, HEIGHT-4 );
			g2.drawLine( width/2+1, 3, width/2+1, HEIGHT-2 );
			g2.drawLine( width/2-2, 4, width/2+1, 4 );
			g2.drawLine( width/2-2, 5, width/2+1, 5 );
			g2.drawLine( width/2-2, HEIGHT-6, width/2+1, HEIGHT-6 );
			g2.drawLine( width/2-2, HEIGHT-5, width/2+1, HEIGHT-5 );
			break;
		case EDIT_ICON:
			g2.drawRect( 3, 1, 10, 14 );
			g2.drawLine( 5, 3, 11, 3 );
			g2.drawLine( 5, 5, 11, 5 );
			g2.drawLine( 5, 7, 11, 7 );
			g2.drawLine( 5, 9, 11, 9 );
			g2.drawLine( 5, 11, 11, 11 );
			if( c.isEnabled() ) g2.setColor( Color.red );
			g2.drawLine( width-1, 2, width-9, 10 );
			g2.drawLine( width-1, 3, width-9, 11 );
			break;

		case DOTTED_HALF_NOTE_ICON:
		case HALF_NOTE_ICON:
			drawMusicalNoteStem( g2 );
			// No break;
		case WHOLE_NOTE_ICON:
			drawMusicalNoteHead( g2 );
			if( isDottedMusicalNote() ) drawMusicalNoteDot(g2);
			break;

		case A128TH_NOTE_ICON:
			drawMusicalNoteFlag( g2, 4 );
			// No break;
		case DOTTED_64TH_NOTE_ICON:
		case A64TH_NOTE_ICON:
			drawMusicalNoteFlag( g2, 3 );
			// No break;
		case DOTTED_32ND_NOTE_ICON:
		case A32ND_NOTE_ICON:
			drawMusicalNoteFlag( g2, 2 );
			// No break;
		case DOTTED_16TH_NOTE_ICON:
		case A16TH_NOTE_ICON:
			drawMusicalNoteFlag( g2, 1 );
			// No break;
		case DOTTED_8TH_NOTE_ICON:
		case A8TH_NOTE_ICON:
			drawMusicalNoteFlag( g2, 0 );
			// No break;
		case DOTTED_QUARTER_NOTE_ICON:
		case QUARTER_NOTE_ICON:
			fillMusicalNoteHead(g2);
			drawMusicalNoteStem(g2);
			if( isDottedMusicalNote() ) drawMusicalNoteDot(g2);
			break;
		}
		g.translate(-x, -y);
	}
	private void drawMusicalNoteFlag( Graphics2D g2, int position ) {
		g2.drawLine(
			width/2 + NOTE_HEAD_WIDTH/2 - 1,
			1 + position * 2,
			width/2 + NOTE_HEAD_WIDTH/2 + 4,
			6 + position * 2
		);
	}
	private void drawMusicalNoteDot( Graphics2D g2 ) {
		g2.fillRect(
			width/2 + NOTE_HEAD_WIDTH/2 + 2,
			HEIGHT - NOTE_HEAD_HEIGHT + 3,
			2, 2
		);
	}
	private void drawMusicalNoteStem( Graphics2D g2 ) {
		g2.fillRect(
			width/2 + NOTE_HEAD_WIDTH/2 - 1,
			1,
			1, HEIGHT - NOTE_HEAD_HEIGHT/2
		);
	}
	private void drawMusicalNoteHead( Graphics2D g2 ) {
		g2.drawOval(
			width/2 - NOTE_HEAD_WIDTH/2,
			HEIGHT - NOTE_HEAD_HEIGHT,
			NOTE_HEAD_WIDTH-1, NOTE_HEAD_HEIGHT-1
		);
	}
	private void fillMusicalNoteHead( Graphics2D g2 ) {
		g2.fillOval(
			width/2 - NOTE_HEAD_WIDTH/2,
			HEIGHT - NOTE_HEAD_HEIGHT,
			NOTE_HEAD_WIDTH, NOTE_HEAD_HEIGHT
		);
	}
}
