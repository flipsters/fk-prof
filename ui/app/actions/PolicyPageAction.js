/**
 * Created by rohit.patiyal on 14/07/17.
 */

export const IS_POLICY_PAGE = 'IS_POLICY_PAGE';
export default function(isSettings) {
  return (dispatch) => {
    return dispatch({type: IS_POLICY_PAGE, isSettings: isSettings});
  };
}
