import { animate, group, query, style, transition, trigger } from '@angular/animations';

const resetRoute = [
    style({ position: 'relative' }),
    query(':enter, :leave', [
        style({
            position: 'absolute',
            top: '0px',
            bottom: '0px',
            left: '0px',
            width: '100%',
            // height: 'calc(100% - 140px)',
            opacity: 0
        })
    ],
        { optional: true })
];
export const fadeAnimation =
    trigger('fadeAnimation', [
        transition('* => *', [
            ...resetRoute,
            query(':enter', [style({ opacity: 0 })], { optional: true }),
            group([
                query(':leave', [style({ opacity: 1 }), animate('200ms', style({ opacity: 0 }))], { optional: true }),
                query(':enter', [style({ opacity: 0 }), animate('200ms', style({ opacity: 1 }))], { optional: true })
            ])
        ])
    ]);

export const fadeSlideIn =
    trigger('fadeSlideIn', [
        transition(":enter", [
            style({ opacity: 0, transform: 'translate(0,5px)' }),
            animate('150ms ease-in-out', style({opacity: 1, transform: 'translate(0,0)'}))
        ]),
    ]);

export const inOut =
    trigger(
        'inOut',
        [
            transition(
                ':enter',
                [
                    style({ width: 0, opacity: 0 }),
                    animate('0.3s ease',
                        style({ width: 170, opacity: 1 }))
                ]
            ),
            transition(
                ':leave',
                [
                    style({ width: 170, opacity: 1 }),
                    animate('0.3s ease',
                        style({ width: 0, opacity: 0 }))
                ]
            )
        ]
    );