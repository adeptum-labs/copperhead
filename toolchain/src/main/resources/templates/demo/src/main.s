; @NAME@ - copper bars
;
; The boot loader starts this program at LOAD while Kickstart still runs. The program takes the machine
; over at once and never hands it back, so the demo runs until the machine is reset.

CUSTOM		=	$dff000
INTREQR		=	$01e
COP1LC		=	$080
DMACON		=	$096
INTENA		=	$09a
INTREQ		=	$09c
BPLCON0		=	$100
COLOR00		=	$180

ALL		=	$7fff			; every bit but set/clear, which stays clear
VERTB		=	5			; the vertical blank bit of the interrupt registers
DMA_COPPER	=	$8280			; set the master and copper bits
NO_PLANES	=	$0200			; colour output on, no bitplanes: only colour 0 shows

STEPS		=	16			; shades from black to full brightness in a bar
BARS		=	6
LINES		=	BARS*STEPS*2		; every bar ramps up and back down
FIRST_LINE	=	$30			; with LINES the bars end at line $f0, inside the 8-bit wait

	org	LOAD

	lea	CUSTOM,a5
	move.w	#ALL,INTENA(a5)		; nothing of Kickstart's may interrupt from here on
	move.w	#ALL,INTREQ(a5)
	move.w	#ALL,DMACON(a5)
	move.l	#copper,COP1LC(a5)	; the copper starts it at the next vertical blank
	move.w	#DMA_COPPER,DMACON(a5)
	moveq	#0,d6			; byte offset of the top line's colour in the table

frame:
	bsr	wait_vblank
	bsr	paint
	addq.w	#2,d6			; the bars roll up one line per frame
	cmp.w	#LINES*2,d6
	blo	frame
	moveq	#0,d6
	bra	frame

; Waits for the next vertical blank. The copper is still above the first bar then, so the colours
; change between two frames and never inside one.
wait_vblank:
	move.w	#1<<VERTB,INTREQ(a5)	; forget a blank that has already passed
.wait:
	btst	#VERTB,INTREQR+1(a5)
	beq	.wait
	rts

; Copies LINES colours, starting D6 bytes into the table, into the copper's colour moves.
paint:
	lea	colours,a0
	adda.w	d6,a0
	lea	bars+6,a1		; the colour word of the first line's move
	move.w	#LINES-1,d0
.line:
	move.w	(a0)+,(a1)
	addq.l	#8,a1			; one wait and one move per line
	dbra	d0,.line
	rts

copper:
	dc.w	BPLCON0,NO_PLANES
	dc.w	COLOR00,0
bars:
line	set	FIRST_LINE
	rept	LINES
	dc.w	(line<<8)|$07,$fffe	; wait for the start of the line
	dc.w	COLOR00,0		; and give it a colour, written by paint
line	set	line+1
	endr
	dc.w	(line<<8)|$07,$fffe
	dc.w	COLOR00,0		; black below the bars
	dc.w	$ffff,$fffe		; a wait for a line that never comes ends the list

; A bar ramps a colour up to full brightness and back; \1 is one step of red, green and blue.
ramp	macro
step	set	0
	rept	STEPS
	dc.w	step*\1
step	set	step+1
	endr
	rept	STEPS
step	set	step-1
	dc.w	step*\1
	endr
	endm

; The bars twice over, so the LINES colours from any offset in the first copy stay inside the table.
colours:
	rept	2
	ramp	$100
	ramp	$110
	ramp	$010
	ramp	$011
	ramp	$001
	ramp	$101
	endr
